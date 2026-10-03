import { useState } from 'react';
import { IonIcon, useIonRouter } from '@ionic/react';
import { searchOutline } from 'ionicons/icons';
import { useTranslation } from 'react-i18next';
import { api, unwrap } from '../../api/client';
import { PanelLayout } from '../../components/PanelLayout';
import { ErrorState, Loading } from '../../components/States';
import { useApi, useSession } from '../../session/Session';

export default function PlaybookList() {
  const { t } = useTranslation();
  const { community } = useSession();
  const router = useIonRouter();
  const [draft, setDraft] = useState('');
  const [q, setQ] = useState('');
  const playbooks = useApi(['playbooks', community, q], () =>
    unwrap(api.GET('/api/playbooks', { params: { query: { community, q: q || undefined } } })),
  );
  const list = playbooks.data ?? [];
  const drafts = list.filter((p) => p.draftVersion != null);

  return (
    <PanelLayout active="playbooks" title={t('panel.playbooks')}>
      <div className="panel-main__head">
        <div className="stack-sm" style={{ flex: '1 1 400px' }}>
          <h1 className="h1">{t('playbooks.title')}</h1>
          <p className="muted" style={{ margin: 0 }}>{t('playbooks.lead')}</p>
        </div>
        <form
          className="row"
          role="search"
          style={{ flex: '1 1 320px' }}
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
      </div>
      {drafts.length > 0 && !q && (
        <div className="notice" style={{ marginBottom: 16 }}>
          {t('playbooks.draftsWaiting', { count: drafts.length })}
        </div>
      )}
      {playbooks.isLoading && <Loading />}
      {playbooks.error && <ErrorState error={playbooks.error} onRetry={() => playbooks.refetch()} />}
      <div className="setup-grid">
        {list.map((p) => (
          <button type="button" key={p.id} className={`card card--link${p.draftVersion != null ? ' card--highlight' : ''}`} onClick={() => router.push(`/playbooks/${p.slug}`)}>
            <div className="card__head">
              {p.draftVersion != null && <span className="tag tag--warn tag--sm">{t('playbooks.draft', { version: p.draftVersion })}</span>}
              {p.currentVersion != null && <span className="tag tag--sm">v{p.currentVersion}</span>}
              <span className="spacer" />
              {p.similarity != null && <span className="tiny muted">{Math.round(p.similarity * 100)}%</span>}
            </div>
            <span className="card__title">{p.title?.text}</span>
            <span className="card__meta">{p.problem?.text}</span>
            <span className="tiny muted">
              {p.origin?.name}
              {p.cases ? ` · ${t('ideas.used', { count: p.cases })}` : ''}
            </span>
          </button>
        ))}
      </div>
    </PanelLayout>
  );
}
