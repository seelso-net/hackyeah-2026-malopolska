import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useIonToast } from '@ionic/react';
import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { streamUrl, type StreamEvent } from '../api/client';
import { invalidate, useSession } from '../session/Session';

export interface Notice {
  id: string;
  type: string;
  caseId?: string;
  reportId?: string;
  data: Record<string, unknown>;
  at: string;
  read: boolean;
}

interface Live {
  connected: boolean;
  notices: Notice[];
  unread: number;
  markAllRead(): void;
}

const LiveContext = createContext<Live>({ connected: false, notices: [], unread: 0, markAllRead: () => {} });

/** Events that change what a screen shows; each refetches the queries it touches. */
function refresh(queryClient: QueryClient, e: StreamEvent) {
  if (e.reportId) {
    void invalidate(queryClient, 'report', e.reportId);
  }
  if (e.caseId) {
    void invalidate(queryClient, 'case', e.caseId);
    void invalidate(queryClient, 'moderation-case', e.caseId);
    void invalidate(queryClient, 'queue');
    void invalidate(queryClient, 'map');
    void invalidate(queryClient, 'my-cases');
    void invalidate(queryClient, 'assignments');
  }
  if (e.type?.startsWith('PLAYBOOK_')) {
    void invalidate(queryClient, 'playbook');
    void invalidate(queryClient, 'playbooks');
  }
  if (e.type === 'CONFIG_CHANGED') {
    void invalidate(queryClient, 'community');
    void invalidate(queryClient, 'config');
  }
}

/** Events worth telling the person about, as opposed to quiet refreshes. */
const NOTEWORTHY = new Set([
  'REPORT_PROCESSED',
  'SAFETY_ALERT',
  'MATCH_SUGGESTED',
  'MATCH_APPROVED',
  'ASSIGNMENT_ACCEPTED',
  'ASSIGNMENT_DECLINED',
  'HELP_OFFERED',
  'UPDATE_POSTED',
  'OUTCOME_RECORDED',
  'PLAYBOOK_DRAFTED',
  'PLAYBOOK_PUBLISHED',
  'STATUS_CHANGED',
]);

export function LiveProvider({ children }: { children: ReactNode }) {
  const { login } = useSession();
  const queryClient = useQueryClient();
  const { t } = useTranslation();
  const [present] = useIonToast();
  const [connected, setConnected] = useState(false);
  const [notices, setNotices] = useState<Notice[]>([]);
  const tRef = useRef(t);
  tRef.current = t;

  useEffect(() => {
    setNotices([]);
    if (!login) {
      return;
    }
    const source = new EventSource(streamUrl(login));
    source.onopen = () => setConnected(true);
    source.onerror = () => setConnected(false);
    source.onmessage = (message) => {
      let e: StreamEvent;
      try {
        e = JSON.parse(message.data);
      } catch {
        return;
      }
      if (e.type === 'HEARTBEAT' || e.type === 'CONNECTED') {
        setConnected(true);
        return;
      }
      refresh(queryClient, e);
      if (!e.type || !NOTEWORTHY.has(e.type)) {
        return;
      }
      const notice: Notice = {
        id: `${e.type}-${e.at}-${Math.random().toString(36).slice(2, 7)}`,
        type: e.type,
        caseId: e.caseId,
        reportId: e.reportId,
        data: (e.data ?? {}) as Record<string, unknown>,
        at: e.at ?? new Date().toISOString(),
        read: false,
      };
      setNotices((list) => [notice, ...list].slice(0, 40));
      const text = describe(tRef.current, notice);
      if (text && toastWorthy(notice)) {
        void present({ message: text, duration: 3500, position: 'top', color: e.type === 'SAFETY_ALERT' ? 'danger' : 'dark' });
      }
    };
    return () => {
      source.close();
      setConnected(false);
    };
  }, [login, queryClient, present]);

  const value = useMemo<Live>(
    () => ({
      connected,
      notices,
      unread: notices.filter((n) => !n.read).length,
      markAllRead: () => setNotices((list) => list.map((n) => ({ ...n, read: true }))),
    }),
    [connected, notices],
  );
  return <LiveContext.Provider value={value}>{children}</LiveContext.Provider>;
}

export function useLive() {
  return useContext(LiveContext);
}

function toastWorthy(n: Notice) {
  if (n.type === 'STATUS_CHANGED') {
    return n.data.to === 'RESOLVED' || n.data.to === 'IN_PROGRESS';
  }
  return n.type !== 'OUTCOME_RECORDED' && n.type !== 'MATCH_SUGGESTED';
}

type T = (key: string, options?: Record<string, unknown>) => string;

/** One sentence per event in the reader's language; events carry codes, never prose. */
export function describe(t: T, n: Notice): string {
  const d = n.data;
  switch (n.type) {
    case 'REPORT_PROCESSED':
      return t('live.REPORT_PROCESSED');
    case 'SAFETY_ALERT':
      return t('live.SAFETY_ALERT');
    case 'MATCH_SUGGESTED':
      return d.playbookId ? t('live.MATCH_SUGGESTED') : t('live.MATCH_SUGGESTED_NONE');
    case 'MATCH_APPROVED':
      return t('live.MATCH_APPROVED', { names: Array.isArray(d.actorNames) ? (d.actorNames as string[]).join(', ') : '' });
    case 'ASSIGNMENT_ACCEPTED':
      return t('live.ASSIGNMENT_ACCEPTED', { name: d.actorName });
    case 'ASSIGNMENT_DECLINED':
      return t(d.redirect ? 'live.ASSIGNMENT_REDIRECTED' : 'live.ASSIGNMENT_DECLINED', { name: d.actorName });
    case 'HELP_OFFERED':
      return t('live.HELP_OFFERED', { name: d.actorName });
    case 'UPDATE_POSTED':
      return d.resolves ? t('live.UPDATE_RESOLVES') : t('live.UPDATE_POSTED');
    case 'OUTCOME_RECORDED':
      return t(d.outcome === 'HELPED' ? 'live.OUTCOME_HELPED' : 'live.OUTCOME_NOT_HELPED');
    case 'PLAYBOOK_DRAFTED':
      return t('live.PLAYBOOK_DRAFTED', { version: d.playbookVersion });
    case 'PLAYBOOK_PUBLISHED':
      return t('live.PLAYBOOK_PUBLISHED', { version: d.playbookVersion });
    case 'STATUS_CHANGED':
      return t('live.STATUS_CHANGED', { status: t(`status.${String(d.to)}`) });
    default:
      return '';
  }
}
