import { useTranslation } from 'react-i18next';

export function KindTag({ kind, small }: { kind?: string; small?: boolean }) {
  const { t } = useTranslation();
  const k = (kind ?? 'NEED').toUpperCase();
  const tone = k === 'NEED' ? 'need' : k === 'IDEA' ? 'idea' : k === 'OFFER' ? 'offer' : 'initiative';
  return <span className={`tag tag--${tone}${small ? ' tag--sm' : ''}`}>{t(`kind.${k}`)}</span>;
}

export function UrgencyLabel({ urgency }: { urgency?: string }) {
  const { t } = useTranslation();
  if (!urgency) {
    return null;
  }
  return <span className={`urgency urgency--${urgency}`}>{t(`urgency.${urgency}`)}</span>;
}

export function ActorKindTag({ kind }: { kind?: string }) {
  const { t } = useTranslation();
  if (!kind) {
    return null;
  }
  return <span className="tag tag--sm tag--square">{t(`actorKind.${kind}`)}</span>;
}

/** MATCH_READY / NO_PLAYBOOK / PROCESSING from the moderator queue. */
export function AiStatusTag({ status }: { status?: string }) {
  const { t } = useTranslation();
  const tone = status === 'MATCH_READY' ? 'ok' : status === 'NO_PLAYBOOK' ? 'warn' : 'idea';
  return <span className={`tag tag--sm tag--${tone}`}>{t(`aiStatus.${status ?? 'PROCESSING'}`)}</span>;
}
