import { useState } from 'react';
import { IonContent, IonIcon, IonPage, useIonRouter } from '@ionic/react';
import { searchOutline } from 'ionicons/icons';
import { useTranslation } from 'react-i18next';
import { api, unwrap } from '../../api/client';
import { BottomNav } from '../../components/BottomNav';
import { ErrorState, Loading } from '../../components/States';
import { useApi, useSession } from '../../session/Session';

/** The ideas library: solutions that already worked somewhere, searchable by meaning. */
export default function Ideas() {
  const { t } = useTranslation();
  const { community } = useSession();
  const router = useIonRouter();
  const [draft, setDraft] = useState('');
  const [q, setQ] = useState('');
  const playbooks = useApi(['playbooks', community, q], () =>
    unwrap(api.GET('/api/playbooks', { params: { query: { community, q: q || undefined } } })),
  );
  const list = (playbooks.data ?? []).filter((p) => p.currentVersion != null);

  return (
    <IonPage>
      <IonContent className="page">
        <div className="column pad stack">
          <div className="stack-sm" style={{ paddingTop: 8 }}>
            <h1 className="h1">{t('ideas.title')}</h1>
            <p className="muted" style={{ margin: 0 }}>{t('ideas.lead')}</p>
          </div>
          <form
            className="row"
            role="search"
            onSubmit={(e) => {
              e.preventDefault();
              setQ(draft.trim());
            }}
          >
            <input className="input" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder={t('ideas.searchPlaceholder')} aria-label={t('ideas.search')} />
            <button type="submit" className="icon-btn" aria-label={t('ideas.search')}>
              <IonIcon icon={searchOutline} />
            </button>
          </form>
          {q && (
            <button type="button" className="btn btn--ghost btn--sm" style={{ alignSelf: 'flex-start' }} onClick={() => { setQ(''); setDraft(''); }}>
              {t('ideas.clear', { q })}
            </button>
          )}
          {playbooks.isLoading && <Loading />}
          {playbooks.error && <ErrorState error={playbooks.error} onRetry={() => playbooks.refetch()} />}
          {playbooks.data && list.length === 0 && <p className="empty">{t('ideas.none')}</p>}
          {list.map((p) => (
            <button type="button" key={p.id} className="card card--link" onClick={() => router.push(`/playbooks/${p.slug}`)}>
              <div className="card__head">
                <span className="tag tag--ok tag--sm">{t('ideas.proven')}</span>
                <span className="small muted">{p.origin?.name}</span>
                <span className="spacer" />
                {p.similarity != null && <span className="tiny muted">{Math.round(p.similarity * 100)}%</span>}
              </div>
              <span className="card__title">{p.title?.text}</span>
              <span className="card__meta">{p.problem?.text}</span>
              <span className="tiny muted">
                {p.cases ? `${t('ideas.used', { count: p.cases })} · ` : ''}v{p.currentVersion}
              </span>
            </button>
          ))}
        </div>
      </IonContent>
      <BottomNav active="ideas" />
    </IonPage>
  );
}
