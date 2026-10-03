import { useState } from 'react';
import { useParams } from 'react-router-dom';
import type { ReactNode } from 'react';
import { IonContent, IonIcon, IonPage, useIonAlert, useIonRouter, useIonToast } from '@ionic/react';
import { arrowBack, checkmarkCircle, sparklesOutline } from 'ionicons/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, unwrap, type VersionView } from '../../api/client';
import { AppLink } from '../../components/AppLink';
import { BottomNav } from '../../components/BottomNav';
import { PanelLayout } from '../../components/PanelLayout';
import { ErrorState, Loading } from '../../components/States';
import { Translated } from '../../components/Translated';
import { dateTime } from '../../lib/format';
import { invalidate, useApi, useSession } from '../../session/Session';

/** Who does a step, in words a resident or a city office understands. */
const ROLE_ORDER = ['INSTITUTION', 'NGO', 'VOLUNTEER_GROUP', 'BUSINESS', 'VOLUNTEER'];

export default function PlaybookPage() {
  const { ref = '' } = useParams();
  const { t } = useTranslation();
  const { lang, isModerator, community } = useSession();
  const router = useIonRouter();
  const queryClient = useQueryClient();
  const [toast] = useIonToast();
  const [ask] = useIonAlert();
  const [view, setView] = useState<'draft' | 'current'>('draft');
  const [busy, setBusy] = useState(false);

  const playbook = useApi(['playbook', ref], () => unwrap(api.GET('/api/playbooks/{ref}', { params: { path: { ref } } })));
  const config = useApi(['community', community], () => unwrap(api.GET('/api/communities/{slug}', { params: { path: { slug: community } } })));
  const category = (code: string) => config.data?.categories?.find((c) => c.code === code)?.label ?? code;
  const p = playbook.data;
  const draft = p?.draft;
  const current = p?.current;
  const showing: VersionView | undefined = draft && (view === 'draft' || !current) ? draft : current ?? draft;
  const isDraft = showing === draft && !!draft;

  const publish = async () => {
    if (!p || !draft) {
      return;
    }
    setBusy(true);
    try {
      await unwrap(api.POST('/api/playbooks/{id}/versions/{number}/publish', { params: { path: { id: p.id!, number: draft.number! } } }));
      void invalidate(queryClient, 'playbook');
      void invalidate(queryClient, 'playbooks');
      setView('current');
      void toast({ message: t('playbook.published', { version: draft.number }), duration: 3000, position: 'top', color: 'dark' });
    } catch (e) {
      void toast({ message: (e as Error).message, duration: 3000, color: 'danger' });
    } finally {
      setBusy(false);
    }
  };

  const discard = () =>
    ask({
      header: t('playbook.discardTitle', { version: draft?.number }),
      message: t('playbook.discardMessage'),
      buttons: [
        { text: t('common.cancel'), role: 'cancel' },
        {
          text: t('playbook.discard'),
          role: 'destructive',
          handler: async () => {
            try {
              await unwrap(api.DELETE('/api/playbooks/{id}/versions/{number}', { params: { path: { id: p!.id!, number: draft!.number! } } }));
              void invalidate(queryClient, 'playbooks');
              if (p?.current) {
                void invalidate(queryClient, 'playbook');
              } else {
                router.push('/playbooks', 'back', 'replace');
              }
            } catch (e) {
              void toast({ message: (e as Error).message, duration: 3000, color: 'danger' });
            }
          },
        },
      ],
    });

  const Frame = isModerator ? PanelFrame : ResidentFrame;
  const steps = showing?.steps ?? [];
  const roles = [...new Set(steps.flatMap((s) => s.roles ?? []))].sort((a, b) => ROLE_ORDER.indexOf(a) - ROLE_ORDER.indexOf(b));
  const results = p?.results;

  return (
    <Frame title={t('panel.playbooks')}>
      {playbook.isLoading && <Loading />}
      {playbook.error && <ErrorState error={playbook.error} onRetry={() => playbook.refetch()} />}
      {p && (
        <div className="stack-lg">
          {draft && p.me?.canPublish && (
            <section className="draft-banner" aria-label={t('playbook.aiDraft')}>
              <IonIcon icon={sparklesOutline} style={{ fontSize: 28, color: 'var(--amber-ink)' }} />
              <div style={{ flex: '1 1 320px' }}>
                <strong style={{ display: 'block', fontSize: 18 }}>
                  {t('playbook.draftTitle', { version: draft.number, cases: (draft.sourceCases ?? []).map((s) => s.community).join(', ') })}
                </strong>
                <span className="small">{t('playbook.draftLead', { helped: results?.residentsHelped ?? 0 })}</span>
              </div>
              <button type="button" className="btn btn--danger btn--sm" onClick={discard} disabled={busy}>
                {t('playbook.discard')}
              </button>
              <button type="button" className="btn btn--sm" onClick={publish} disabled={busy}>
                <IonIcon icon={checkmarkCircle} aria-hidden="true" /> {t('playbook.publish', { version: draft.number })}
              </button>
            </section>
          )}

          <div className="stack-sm">
            <span className="small muted">
              <AppLink to="/playbooks" direction="back">{t('panel.playbooks')}</AppLink>
              {p.categoryCodes?.length ? ` / ${p.categoryCodes.map(category).join(', ')}` : ''}
            </span>
            <h1 className="h1" style={{ fontSize: 34 }}>{p.title?.text}</h1>
            <div className="row-wrap">
              {(p.categoryCodes ?? []).map((c) => (
                <span key={c} className="tag">{category(c)}</span>
              ))}
              {results && results.communities && results.communities.length > 0 && (
                <span className="tag tag--ok">{t('playbook.usedIn', { count: results.communities.length })}</span>
              )}
              {showing && (
                <span className={`tag ${isDraft ? 'tag--warn' : ''}`}>
                  {t(isDraft ? 'playbook.versionDraft' : 'playbook.versionCurrent', { version: showing.number })}
                </span>
              )}
              {p.origin && <span className="small muted">{t('playbook.from', { origin: p.origin.name })}</span>}
            </div>
            {draft && current && (
              <div className="segmented" role="group" aria-label={t('playbook.versions')} style={{ alignSelf: 'flex-start', marginTop: 6 }}>
                <button type="button" aria-pressed={view === 'draft'} onClick={() => setView('draft')}>
                  {t('playbook.viewDraft', { version: draft.number })}
                </button>
                <button type="button" aria-pressed={view === 'current'} onClick={() => setView('current')}>
                  {t('playbook.viewCurrent', { version: current.number })}
                </button>
              </div>
            )}
          </div>

          <div className="pb-layout">
            <div className="pb-main">
              {p.problem?.text && (
                <section className="stack-sm">
                  <h2 className="h3">{t('playbook.problem')}</h2>
                  <Translated value={p.problem} as="p" />
                </section>
              )}
              <section className="stack-sm">
                <h2 className="h3">{t('playbook.whatWorked')}</h2>
                <ol className="pb-steps">
                  {steps.map((s) => (
                    <li key={s.position} className={`pb-step${s.change === 'NEW' ? ' pb-step--new' : s.change === 'CHANGED' ? ' pb-step--changed' : ''}`}>
                      <span className="pb-step__n">{s.position}</span>
                      <div className="stack-sm" style={{ flex: 1, minWidth: 0 }}>
                        {s.change && (
                          <span className={`tag tag--sm ${s.change === 'NEW' ? 'tag--ok' : 'tag--warn'}`} style={{ alignSelf: 'flex-start' }}>
                            {t(s.change === 'NEW' ? 'playbook.newIn' : 'playbook.changedIn', { version: showing?.number })}
                          </span>
                        )}
                        <span className="pb-step__title">{s.title?.text}</span>
                        <Translated value={s.description} as="p" className="small muted" />
                        <div className="row-wrap">
                          {(s.roles ?? []).map((r) => (
                            <span key={r} className="tag tag--sm tag--square">{t(`actorKind.${r}`)}</span>
                          ))}
                        </div>
                      </div>
                    </li>
                  ))}
                </ol>
              </section>
              <p className="tiny muted">{isDraft ? t('playbook.aiFootnote') : t('playbook.publishedBy', { name: showing?.publishedBy ?? '—', when: dateTime(showing?.publishedAt, lang) })}</p>
            </div>

            <aside className="pb-aside">
              {showing?.changeNotes?.text && (
                <section className="card stack-sm">
                  <span className="eyebrow">{t('playbook.whatChanged', { version: showing.number })}</span>
                  <Translated value={showing.changeNotes} as="p" className="small" />
                </section>
              )}
              {results && (
                <section className="card stack-sm">
                  <span className="eyebrow">{t('playbook.results')}</span>
                  <dl className="kv">
                    <dt>{t('playbook.cases')}</dt>
                    <dd>{results.cases}</dd>
                    <dt>{t('playbook.closed')}</dt>
                    <dd>{results.closedCases}</dd>
                    <dt>{t('playbook.helped')}</dt>
                    <dd>{results.residentsHelped}</dd>
                    <dt>{t('playbook.where')}</dt>
                    <dd>{(results.communities ?? []).join(', ') || '—'}</dd>
                  </dl>
                </section>
              )}
              {showing?.effort && (
                <section className="card stack-sm">
                  <span className="eyebrow">{t('playbook.effort')}</span>
                  <dl className="kv">
                    <dt>{t('playbook.coordinator')}</dt>
                    <dd>{showing.effort.coordinatorTime ?? '—'}</dd>
                    <dt>{t('playbook.budget')}</dt>
                    <dd>{showing.effort.budget ?? '—'}</dd>
                    <dt>{t('playbook.firstHelp')}</dt>
                    <dd>{showing.effort.firstHelp ?? '—'}</dd>
                  </dl>
                </section>
              )}
              {roles.length > 0 && (
                <section className="card stack-sm">
                  <span className="eyebrow">{t('playbook.involve')}</span>
                  {roles.map((r) => (
                    <span key={r} className="small">
                      <strong>{t(`actorKind.${r}`)}</strong> · {t('playbook.stepsCount', { count: steps.filter((s) => s.roles?.includes(r)).length })}
                    </span>
                  ))}
                </section>
              )}
              {(showing?.sourceCases ?? []).length > 0 && (
                <section className="card stack-sm">
                  <span className="eyebrow">{t('playbook.builtFrom', { count: showing!.sourceCases!.length })}</span>
                  {showing!.sourceCases!.map((s) => (
                    <AppLink key={s.id} to={`/case/${s.id}`} className="small">
                      {s.number} · {s.community}
                    </AppLink>
                  ))}
                </section>
              )}
              {(p.versions ?? []).length > 1 && (
                <section className="card stack-sm">
                  <span className="eyebrow">{t('playbook.history')}</span>
                  {p.versions!.map((v) => (
                    <span key={v.number} className="small">
                      <strong>v{v.number}</strong> · {t(`playbook.status.${v.status}`)} · {t(`playbook.by.${v.draftedBy}`)}
                      {v.publishedAt ? ` · ${dateTime(v.publishedAt, lang)}` : ''}
                    </span>
                  ))}
                </section>
              )}
            </aside>
          </div>
        </div>
      )}
    </Frame>
  );
}

function PanelFrame({ title, children }: { title: string; children: ReactNode }) {
  return (
    <PanelLayout active="playbooks" title={title}>
      {children}
    </PanelLayout>
  );
}

/** Residents open playbooks from their case or the ideas library: a phone layout with a back button. */
function ResidentFrame({ children }: { title: string; children: ReactNode }) {
  const { t } = useTranslation();
  const router = useIonRouter();
  return (
    <IonPage>
      <IonContent className="page">
        <div className="column" style={{ maxWidth: 1080 }}>
          <header className="mobile-header">
            <button type="button" className="icon-btn" aria-label={t('common.back')} onClick={() => (router.canGoBack() ? router.goBack() : router.push('/ideas', 'back'))}>
              <IonIcon icon={arrowBack} />
            </button>
            <span className="mobile-header__title">{t('playbook.headerResident')}</span>
          </header>
          <div className="pad">{children}</div>
        </div>
      </IonContent>
      <BottomNav active="ideas" />
    </IonPage>
  );
}
