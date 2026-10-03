import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { IonContent, IonIcon, IonPage, IonSpinner, useIonRouter } from '@ionic/react';
import { alertCircleOutline, arrowBack, callOutline, checkmarkCircle, createOutline, locationOutline, peopleOutline } from 'ionicons/icons';
import { useTranslation } from 'react-i18next';
import { api, mediaUrl, unwrap, type SubmitReport } from '../../api/client';
import { Translated } from '../../components/Translated';
import { ErrorState, Loading } from '../../components/States';
import { KindTag } from '../../components/Tags';
import { distance } from '../../lib/format';
import { useApi, useSession } from '../../session/Session';

export default function ReviewReport() {
  const { id = '' } = useParams();
  const { t } = useTranslation();
  const { community, lang } = useSession();
  const router = useIonRouter();
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState('');
  const [summary, setSummary] = useState('');
  const [category, setCategory] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [offerDone, setOfferDone] = useState(false);

  const report = useApi(['report', id], () => unwrap(api.GET('/api/reports/{id}', { params: { path: { id } } })), {
    refetchInterval: (q) => (q.state.data?.status === 'RECEIVED' ? 1000 : false),
  });
  const config = useApi(['community', community], () => unwrap(api.GET('/api/communities/{slug}', { params: { path: { slug: community } } })));
  const r = report.data;
  const ai = r?.ai;

  useEffect(() => {
    if (ai) {
      setTitle(ai.title?.text ?? '');
      setSummary(ai.summary?.text ?? '');
      setCategory(ai.categoryCode ?? '');
    }
  }, [ai]);

  const submit = async (caseId?: string) => {
    setBusy(true);
    setError(null);
    const body: SubmitReport = {};
    if (caseId) {
      body.caseId = caseId;
    }
    if (editing && ai) {
      if (title.trim() && title.trim() !== ai.title?.text) {
        body.title = title.trim();
      }
      if (summary.trim() && summary.trim() !== ai.summary?.text) {
        body.summary = summary.trim();
      }
      if (category && category !== ai.categoryCode) {
        body.categoryCode = category;
      }
    }
    try {
      const result = await unwrap(api.POST('/api/reports/{id}/submit', { params: { path: { id } }, body }));
      if (result.result === 'OFFER_RECORDED') {
        setOfferDone(true);
      } else {
        router.push(`/case/${result.caseId}`, 'forward', 'replace');
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const audio = r?.media?.find((m) => m.kind === 'AUDIO');
  const photos = r?.media?.filter((m) => m.kind === 'PHOTO') ?? [];
  const similar = r?.similarCase;
  const urgent = ai?.urgency === 'HIGH' || ai?.urgency === 'EMERGENCY';

  if (offerDone) {
    return (
      <IonPage>
        <IonContent className="page">
          <div className="column center-fill">
            <IonIcon icon={checkmarkCircle} style={{ fontSize: 64, color: 'var(--brand)' }} />
            <h1 className="h2">{t('review.offerThanksTitle')}</h1>
            <p className="muted">{t('review.offerThanks')}</p>
            <button type="button" className="btn" onClick={() => router.push('/home', 'root', 'replace')}>
              {t('review.backToMap')}
            </button>
          </div>
        </IonContent>
      </IonPage>
    );
  }

  return (
    <IonPage>
      <IonContent className="page">
        <div className="column">
          <header className="mobile-header">
            <button type="button" className="icon-btn" aria-label={t('common.back')} onClick={() => router.push('/home', 'back')}>
              <IonIcon icon={arrowBack} />
            </button>
            <span className="mobile-header__title">{t('review.title')}</span>
            <span className="spacer" />
            <span className="steps-label">{t('report.step', { n: 2 })}</span>
          </header>

          {report.isLoading && <Loading />}
          {report.error && <ErrorState error={report.error} onRetry={() => report.refetch()} />}
          {r && (
            <div className="pad stack-lg" style={{ paddingTop: 4 }}>
              <div className="stack-sm">
                <h1 className="h2">{t('review.question')}</h1>
                <p className="muted" style={{ margin: 0 }}>
                  {r.status === 'RECEIVED' ? t('review.reading') : r.inputMode === 'VOICE' ? t('review.fromVoice') : t('review.fromText')}
                </p>
              </div>

              {r.status === 'RECEIVED' || !ai ? (
                <div className="card" style={{ alignItems: 'center', padding: 24, gap: 12 }}>
                  <IonSpinner name="dots" color="primary" />
                  <span className="muted small">{t('review.aiWorking')}</span>
                  <p style={{ margin: 0 }}>“{r.text?.text}”</p>
                </div>
              ) : (
                <article className="card stack" style={{ gap: 12 }}>
                  <div className="card__head">
                    <KindTag kind={r.kind} />
                    {!editing && <span className="small muted">{ai.categoryLabel}</span>}
                    <span className="spacer" />
                    {!editing && r.status === 'PROCESSED' && (
                      <button type="button" className="btn btn--ghost btn--sm" onClick={() => setEditing(true)}>
                        <IonIcon icon={createOutline} aria-hidden="true" /> {t('review.edit')}
                      </button>
                    )}
                  </div>
                  {editing ? (
                    <div className="stack">
                      <label className="field">
                        <span className="field__label">{t('review.category')}</span>
                        <select className="select" value={category} onChange={(e) => setCategory(e.target.value)}>
                          {(config.data?.categories ?? []).map((c) => (
                            <option key={c.code} value={c.code}>{c.label}</option>
                          ))}
                        </select>
                      </label>
                      <label className="field">
                        <span className="field__label">{t('review.titleLabel')}</span>
                        <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} />
                      </label>
                      <label className="field">
                        <span className="field__label">{t('review.summaryLabel')}</span>
                        <textarea className="textarea" value={summary} onChange={(e) => setSummary(e.target.value)} />
                      </label>
                    </div>
                  ) : (
                    <div className="stack-sm">
                      <strong style={{ fontSize: 18 }}>{ai.title?.text}</strong>
                      <Translated value={ai.summary} as="p" className="small" />
                    </div>
                  )}
                  <dl className="kv">
                    <dt>{t('review.place')}</dt>
                    <dd>
                      <IonIcon icon={locationOutline} aria-hidden="true" /> {r.address || t('review.pinned')}
                    </dd>
                    <dt>{t('review.urgency')}</dt>
                    <dd style={{ color: urgent ? 'var(--danger)' : undefined }}>{t(`review.urgency${ai.urgency ?? 'MEDIUM'}`)}</dd>
                  </dl>
                  {audio && (
                    <div className="stack-sm">
                      <audio controls src={mediaUrl(audio.url)} style={{ width: '100%', height: 40 }} />
                      <span className="tiny muted">{t('review.voiceNote')}</span>
                    </div>
                  )}
                  {photos.length > 0 && (
                    <div className="photo-strip">
                      {photos.map((p) => (
                        <img key={p.id} src={mediaUrl(p.url)} alt="" />
                      ))}
                    </div>
                  )}
                </article>
              )}

              {ai?.safetyConcern && (
                <div className="notice notice--danger">
                  <IonIcon icon={alertCircleOutline} aria-hidden="true" />
                  <span>{t('review.safety')}</span>
                </div>
              )}

              {r.status === 'PROCESSED' && r.kind !== 'OFFER' && similar && (
                <section className="card card--tint stack" style={{ gap: 10 }}>
                  <span className="eyebrow">{t('review.similarTitle')}</span>
                  <strong style={{ fontSize: 18 }}>{similar.title?.text}</strong>
                  <div className="row small muted">
                    <IonIcon icon={peopleOutline} aria-hidden="true" />
                    {t('review.similarMeta', { count: similar.reportCount ?? 0, distance: distance(similar.distanceMeters, lang) })}
                    <span className="spacer" />
                    <span className="tag tag--sm">{similar.statusLabel}</span>
                  </div>
                  <p className="small" style={{ margin: 0 }}>{t('review.similarWhy')}</p>
                  <button type="button" className="btn btn--lg btn--block" disabled={busy} onClick={() => submit(similar.id)}>
                    {t('review.join')}
                  </button>
                  <button type="button" className="btn btn--ghost" disabled={busy} onClick={() => submit()}>
                    {t('review.sendNew')}
                  </button>
                </section>
              )}

              {r.status === 'PROCESSED' && (r.kind === 'OFFER' || !similar) && (
                <button type="button" className="btn btn--lg btn--block" disabled={busy} onClick={() => submit()}>
                  {r.kind === 'OFFER' ? t('review.sendOffer') : t('review.send')}
                </button>
              )}

              {r.status === 'SUBMITTED' && r.caseId && (
                <button type="button" className="btn btn--lg btn--block" onClick={() => router.push(`/case/${r.caseId}`, 'forward')}>
                  {t('review.openCase')}
                </button>
              )}

              {error && <div className="notice notice--danger">{error}</div>}

              <a className="notice notice--danger" href="tel:112" style={{ textDecoration: 'none', fontWeight: 700 }}>
                <IonIcon icon={callOutline} aria-hidden="true" />
                {t('review.call112')}
              </a>
            </div>
          )}
        </div>
      </IonContent>
    </IonPage>
  );
}
