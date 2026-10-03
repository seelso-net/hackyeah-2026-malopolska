import { useTranslation } from 'react-i18next';
import { mediaUrl, type CommunityView, type TimelineItem } from '../api/client';
import { dateTime } from '../lib/format';
import { useSession } from '../session/Session';
import { Translated } from './Translated';

export function statusName(config: CommunityView | undefined, status: string | undefined, fallback: (s: string) => string) {
  const label = config?.statuses?.find((s) => s.status === status)?.label;
  return label ?? fallback(status ?? '');
}

/** Everything that happened on a case, as sentences in the reader's language. */
export function Timeline({ items, config }: { items: TimelineItem[]; config?: CommunityView }) {
  const { t } = useTranslation();
  const { lang } = useSession();
  const status = (s?: unknown) => statusName(config, String(s ?? ''), (x) => t(`status.${x}`));

  const sentence = (e: TimelineItem): string => {
    const d = (e.data ?? {}) as Record<string, unknown>;
    switch (e.type) {
      case 'REPORT_ADDED':
        return e.authorName ? t('timeline.reportBy', { name: e.authorName }) : t('timeline.reportAdded');
      case 'STATUS_CHANGED':
        return d.reason === 'REOPENED'
          ? t('timeline.reopened')
          : d.reason === 'AUTO_CLOSED'
            ? t('timeline.autoClosed')
            : t('timeline.status', { status: status(d.to) });
      case 'MATCH_SUGGESTED':
        return d.playbookId ? t('timeline.suggested') : t('timeline.suggestedNone');
      case 'MATCH_APPROVED':
        return t('timeline.approved', {
          name: e.authorName ?? t('role.MODERATOR'),
          names: Array.isArray(d.actorNames) ? (d.actorNames as string[]).join(', ') : '',
        });
      case 'ASSIGNMENT_ACCEPTED':
        return t('timeline.accepted', { name: e.actorName });
      case 'ASSIGNMENT_DECLINED':
        return t(d.redirect ? 'timeline.redirected' : 'timeline.declined', { name: e.actorName, note: d.note ? `: “${d.note}”` : '' });
      case 'HELP_OFFERED':
        return t('timeline.helpOffered', { name: e.actorName ?? e.authorName, note: d.note ? `: “${d.note}”` : '' });
      case 'UPDATE_POSTED':
        return t(d.resolves ? 'timeline.updateResolves' : 'timeline.update', { name: e.actorName ?? e.authorName ?? '' });
      case 'OUTCOME_RECORDED':
        return t(d.outcome === 'HELPED' ? 'timeline.helped' : 'timeline.notHelped', { name: e.authorName ?? t('timeline.aResident') });
      case 'PLAYBOOK_DRAFTED':
        return t('timeline.drafted', { version: d.playbookVersion });
      default:
        return e.type ?? '';
    }
  };

  return (
    <ol className="timeline">
      {items.map((e) => (
        <li key={e.id} className={e.type === 'UPDATE_POSTED' || e.type === 'MATCH_APPROVED' ? 'key' : undefined}>
          <div style={{ fontWeight: e.type === 'UPDATE_POSTED' ? 700 : 400 }}>{sentence(e)}</div>
          {e.text && <Translated value={e.text} as="div" />}
          {(e.media ?? []).length > 0 && (
            <div className="photo-strip" style={{ marginTop: 6 }}>
              {e.media!.map((m) => (
                <img key={m.id} src={mediaUrl(m.url)} alt="" style={{ height: 80 }} />
              ))}
            </div>
          )}
          <div className="timeline__when">{dateTime(e.at, lang)}</div>
        </li>
      ))}
    </ol>
  );
}
