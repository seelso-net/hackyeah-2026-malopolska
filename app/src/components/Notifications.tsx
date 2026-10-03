import { useState } from 'react';
import { IonIcon, useIonRouter } from '@ionic/react';
import { notificationsOutline } from 'ionicons/icons';
import { useTranslation } from 'react-i18next';
import { describe, useLive, type Notice } from '../live/Live';
import { useSession } from '../session/Session';
import { timeAgo } from '../lib/format';

/** The bell: everything the live stream delivered since this screen opened. */
export function Bell({ flat, dark }: { flat?: boolean; dark?: boolean }) {
  const { t } = useTranslation();
  const { notices, unread, markAllRead, connected } = useLive();
  const { lang, isModerator, isDoer } = useSession();
  const router = useIonRouter();
  const [open, setOpen] = useState(false);

  const go = (n: Notice) => {
    setOpen(false);
    if (n.caseId) {
      router.push(isModerator ? `/moderation?case=${n.caseId}` : isDoer && !n.type.startsWith('OUTCOME') ? '/doer' : `/case/${n.caseId}`);
    } else if (n.reportId) {
      router.push(isModerator ? '/moderation' : `/report/${n.reportId}`);
    } else if (n.type.startsWith('PLAYBOOK') && typeof n.data.playbookId === 'string') {
      router.push(`/playbooks/${n.data.playbookId}`);
    }
  };

  return (
    <>
      <button
        type="button"
        className={`icon-btn${flat ? ' icon-btn--flat' : ''}`}
        style={dark ? { background: 'transparent', color: '#fff', boxShadow: 'inset 0 0 0 1px #3b4752' } : undefined}
        aria-label={t('live.bell', { count: unread })}
        aria-expanded={open}
        onClick={() => {
          setOpen(!open);
          if (open) {
            markAllRead();
          }
        }}
      >
        <IonIcon icon={notificationsOutline} />
        {unread > 0 && <span className="dot" />}
      </button>
      {open && (
        <div className="notif-panel" role="dialog" aria-label={t('live.title')}>
          <div className="row" style={{ padding: '4px 8px 8px' }}>
            <strong>{t('live.title')}</strong>
            <span className="spacer" />
            <span className={`live-dot${connected ? ' live-dot--on' : ''}`}>{connected ? t('live.connected') : t('live.offline')}</span>
          </div>
          {notices.length === 0 && <p className="empty small">{t('live.empty')}</p>}
          {notices.map((n) => (
            <button type="button" key={n.id} className={`notif${n.read ? '' : ' notif--new'}`} onClick={() => go(n)}>
              <span className="stack-sm" style={{ gap: 2 }}>
                <span style={{ fontWeight: 700, fontSize: 15 }}>{describe(t, n)}</span>
                <span className="tiny muted">{timeAgo(n.at, lang)}</span>
              </span>
            </button>
          ))}
          {notices.length > 0 && (
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => { markAllRead(); setOpen(false); }}>
              {t('live.markRead')}
            </button>
          )}
        </div>
      )}
    </>
  );
}
