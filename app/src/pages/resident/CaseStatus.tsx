import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { IonContent, IonIcon, IonPage, useIonRouter, useIonToast } from '@ionic/react';
import { arrowBack, checkmarkCircle, chevronForward, libraryOutline, notificationsOutline } from 'ionicons/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, mediaUrl, unwrap } from '../../api/client';
import { Avatar } from '../../components/Avatar';
import { ErrorState, Loading } from '../../components/States';
import { Stepper } from '../../components/Stepper';
import { ActorKindTag, KindTag } from '../../components/Tags';
import { Timeline, statusName } from '../../components/Timeline';
import { Translated } from '../../components/Translated';
import { timeAgo } from '../../lib/format';
import { invalidate, useApi, useSession } from '../../session/Session';

const PROGRESS: Record<string, number> = {
  NEW: 1,
  TRIAGED: 1,
  SUGGESTED: 1,
  MATCHED: 2,
  IN_PROGRESS: 3,
  RESOLVED: 4,
  CONFIRMED: 5,
  CLOSED: 5,
};

export default function CaseStatus() {
  const { id = '' } = useParams();
  const { t } = useTranslation();
  const { lang, community, isModerator } = useSession();
  const router = useIonRouter();
  const queryClient = useQueryClient();
  const [toast] = useIonToast();
  const [busy, setBusy] = useState(false);
  const [showAll, setShowAll] = useState(false);

  const detail = useApi(['case', id], () => unwrap(api.GET('/api/cases/{id}', { params: { path: { id } } })));
  const c = detail.data;
  const slug = c?.community?.slug ?? community;
  const config = useApi(['community', slug], () => unwrap(api.GET('/api/communities/{slug}', { params: { path: { slug } } })));

  const refresh = () => {
    void invalidate(queryClient, 'case', id);
    void invalidate(queryClient, 'my-cases');
    void invalidate(queryClient, 'map');
  };

  const answer = async (outcome: 'HELPED' | 'NOT_HELPED') => {
    setBusy(true);
    try {
      await unwrap(api.POST('/api/cases/{id}/outcome', { params: { path: { id } }, body: { outcome } }));
      refresh();
      void toast({ message: outcome === 'HELPED' ? t('case.thanksHelped') : t('case.thanksNotYet'), duration: 3000, position: 'top', color: 'dark' });
    } catch (e) {
      void toast({ message: (e as Error).message, duration: 3000, color: 'danger' });
    } finally {
      setBusy(false);
    }
  };

  const support = async () => {
    try {
      await unwrap(api.POST('/api/cases/{id}/support', { params: { path: { id } } }));
      refresh();
      void toast({ message: t('home.supported'), duration: 2500, position: 'top', color: 'dark' });
    } catch (e) {
      void toast({ message: (e as Error).message, duration: 3000, color: 'danger' });
    }
  };

  if (detail.isLoading) {
    return (
      <IonPage>
        <IonContent className="page"><Loading /></IonContent>
      </IonPage>
    );
  }
  if (detail.error || !c) {
    return (
      <IonPage>
        <IonContent className="page"><ErrorState error={detail.error ?? 'Not found'} onRetry={() => detail.refetch()} /></IonContent>
      </IonPage>
    );
  }

  const me = c.me ?? {};
  const label = (s: string) => statusName(config.data, s, (x) => t(`status.${x}`));
  const steps = [
    { label: t('case.reported') },
    { label: label('MATCHED') },
    { label: label('IN_PROGRESS') },
    { label: label('RESOLVED') },
    { label: label('CONFIRMED') },
  ];
  const timeline = c.timeline ?? [];
  const updates = timeline.filter((e) => e.type === 'UPDATE_POSTED' && e.text);
  const latest = updates[updates.length - 1];
  const others = Math.max(0, (c.reportCount ?? 0) + (c.supporterCount ?? 0) - (me.participant ? 1 : 0));
  const helpers = c.helpers ?? [];
  const closed = c.status === 'CLOSED' || c.status === 'CONFIRMED';

  return (
    <IonPage>
      <IonContent className="page">
        <div className="column">
          <header className="mobile-header">
            <button type="button" className="icon-btn" aria-label={t('common.back')} onClick={() => (router.canGoBack() ? router.goBack() : router.push('/home', 'back'))}>
              <IonIcon icon={arrowBack} />
            </button>
            <span className="mobile-header__title">{me.participant ? t('case.yourCase') : t('case.title')}</span>
            <span className="spacer" />
            {me.participant && (
              <span className="tag tag--ok" style={{ height: 36, padding: '0 14px' }}>
                <IonIcon icon={notificationsOutline} aria-hidden="true" /> {t('case.following')}
              </span>
            )}
          </header>

          <div className="pad stack-lg" style={{ paddingTop: 4 }}>
            <div className="stack-sm">
              <div className="row-wrap">
                <span className={`tag ${me.canAnswerOutcome ? 'tag--warn' : closed ? 'tag--ok' : ''}`}>
                  {me.canAnswerOutcome ? t('case.pleaseConfirm', { status: c.statusLabel }) : c.statusLabel}
                </span>
                <KindTag kind={c.kind} small />
                <span className="small muted">{c.categoryLabel} · {c.number}</span>
              </div>
              <h1 className="h2">{c.title?.text}</h1>
              <p className="small muted" style={{ margin: 0 }}>
                {me.participant ? t('case.youAnd', { count: others }) : t('case.people', { count: others })}
                {c.areaLabel ? ` · ${c.areaLabel}` : ''}
              </p>
            </div>

            <Stepper steps={steps} done={PROGRESS[c.status ?? 'NEW'] ?? 1} />

            {latest ? (
              <article className="card" style={{ gap: 10, padding: 16 }}>
                <div className="row">
                  <Avatar name={latest.actorName ?? latest.authorName} />
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 700, fontSize: 15 }}>{latest.actorName ?? latest.authorName}</div>
                    <div className="tiny muted">{t('case.latestUpdate', { when: timeAgo(latest.at, lang) })}</div>
                  </div>
                </div>
                <Translated value={latest.text} as="p" className="small" />
                {(latest.media ?? []).length > 0 && (
                  <div className="photo-strip">
                    {latest.media!.map((m) => (
                      <img key={m.id} src={mediaUrl(m.url)} alt="" />
                    ))}
                  </div>
                )}
              </article>
            ) : (
              <div className="notice notice--info">
                <span>{t(`case.waiting.${c.status ?? 'NEW'}`, { defaultValue: t('case.waiting.NEW') })}</span>
              </div>
            )}

            {helpers.length > 0 && (
              <section className="stack-sm">
                <h2 className="h3">{t('case.whoHelps')}</h2>
                {helpers.map((h) => (
                  <div className="row card" key={h.assignmentId} style={{ flexDirection: 'row', gap: 12 }}>
                    <Avatar name={h.name} />
                    <span style={{ flex: 1, minWidth: 0 }}>
                      <span style={{ display: 'block', fontWeight: 700 }}>{h.name}</span>
                      <span className="tiny muted">{t(`helperStatus.${h.status}`)}</span>
                    </span>
                    <ActorKindTag kind={h.kind} />
                  </div>
                ))}
              </section>
            )}

            {c.playbook && (
              <button type="button" className="card card--link row" style={{ flexDirection: 'row', gap: 12 }} onClick={() => router.push(`/playbooks/${c.playbook!.slug}`)}>
                <span className="playbook-chip__icon">
                  <IonIcon icon={libraryOutline} />
                </span>
                <span style={{ flex: 1, minWidth: 0 }}>
                  <span style={{ display: 'block', fontWeight: 700 }}>{t('case.howSolved', { title: c.playbook.title?.text })}</span>
                  <span className="tiny muted">{t('case.playbookFrom', { origin: c.playbook.origin })}</span>
                </span>
                <IonIcon icon={chevronForward} />
              </button>
            )}

            {(c.tasks ?? []).length > 0 && (
              <section className="stack-sm">
                <h2 className="h3">{t('case.plan')}</h2>
                <ul className="stack-sm" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                  {c.tasks!.map((task) => (
                    <li key={task.id} className="row small">
                      <IonIcon icon={checkmarkCircle} style={{ color: task.done ? 'var(--brand)' : 'var(--line-3)', fontSize: 20 }} />
                      <span style={{ textDecoration: task.done ? 'line-through' : undefined }}>{task.title?.text}</span>
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {(c.outcomes?.helped ?? 0) > 0 && (
              <p className="small muted" style={{ margin: 0 }}>{t('case.helpedCount', { count: c.outcomes!.helped })}</p>
            )}

            {!me.participant && c.status !== 'CLOSED' && (
              <div className="btn-row">
                <button type="button" className="btn btn--secondary" onClick={support}>{c.kind === 'IDEA' ? t('home.support') : t('home.meToo')}</button>
              </div>
            )}
            {me.assignmentId && (
              <button type="button" className="btn btn--soft" onClick={() => router.push('/doer')}>{t('case.openInbox')}</button>
            )}
            {isModerator && (
              <button type="button" className="btn btn--soft" onClick={() => router.push(`/moderation?case=${c.id}`)}>{t('case.openModeration')}</button>
            )}

            <section className="stack-sm">
              <button type="button" className="btn btn--ghost" style={{ alignSelf: 'flex-start' }} onClick={() => setShowAll(!showAll)}>
                {showAll ? t('case.hideHistory') : t('case.showHistory', { count: timeline.length })}
              </button>
              {showAll && <Timeline items={timeline} config={config.data} />}
            </section>
          </div>
        </div>
      </IonContent>
      {me.canAnswerOutcome && (
        <div className="footer-bar">
          <div className="footer-bar__inner">
            <p style={{ margin: '0 0 10px', fontSize: 17, fontWeight: 700 }}>{t('case.didItHelp')}</p>
            <div className="btn-row">
              <button type="button" className="btn btn--secondary" disabled={busy} onClick={() => answer('NOT_HELPED')}>
                {t('case.notYet')}
              </button>
              <button type="button" className="btn" style={{ flex: 1.6 }} disabled={busy} onClick={() => answer('HELPED')}>
                <IonIcon icon={checkmarkCircle} aria-hidden="true" /> {t('case.yesHelped')}
              </button>
            </div>
          </div>
        </div>
      )}
      {me.outcome && (
        <div className="footer-bar">
          <div className="footer-bar__inner small">{t(me.outcome === 'HELPED' ? 'case.youSaidHelped' : 'case.youSaidNotYet')}</div>
        </div>
      )}
    </IonPage>
  );
}
