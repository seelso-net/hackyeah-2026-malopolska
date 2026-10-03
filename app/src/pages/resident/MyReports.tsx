import { IonContent, IonPage, useIonRouter } from '@ionic/react';
import { useTranslation } from 'react-i18next';
import { api, unwrap } from '../../api/client';
import { BottomNav } from '../../components/BottomNav';
import { Bell } from '../../components/Notifications';
import { ErrorState, Loading } from '../../components/States';
import { KindTag } from '../../components/Tags';
import { timeAgo } from '../../lib/format';
import { useApi, useSession } from '../../session/Session';

export default function MyReports() {
  const { t } = useTranslation();
  const { lang } = useSession();
  const router = useIonRouter();
  const mine = useApi(['my-cases'], () => unwrap(api.GET('/api/me/cases')));
  const list = mine.data ?? [];
  const waiting = list.filter((c) => c.canAnswerOutcome);

  return (
    <IonPage>
      <IonContent className="page">
        <div className="column pad stack">
          <div className="row" style={{ paddingTop: 8 }}>
            <h1 className="h1">{t('my.title')}</h1>
            <span className="spacer" />
            <Bell flat />
          </div>
          {waiting.length > 0 && <div className="notice">{t('my.confirm', { count: waiting.length })}</div>}
          {mine.isLoading && <Loading />}
          {mine.error && <ErrorState error={mine.error} onRetry={() => mine.refetch()} />}
          {mine.data && list.length === 0 && (
            <div className="empty stack">
              <span>{t('my.empty')}</span>
              <button type="button" className="btn" onClick={() => router.push('/report/new')}>{t('my.reportNow')}</button>
            </div>
          )}
          {list.map((c) => (
            <button type="button" key={c.id} className={`card card--link${c.canAnswerOutcome ? ' card--highlight' : ''}`} onClick={() => router.push(`/case/${c.id}`)}>
              <div className="card__head">
                <KindTag kind={c.kind} small />
                <span className="small muted">{c.categoryLabel}</span>
                <span className="spacer" />
                <span className="tag tag--sm">{c.statusLabel}</span>
              </div>
              <span className="card__title">{c.title?.text}</span>
              <span className="card__meta">
                {t(`my.role.${c.role}`)} · {timeAgo(c.joinedAt, lang)} · {c.community?.name}
              </span>
              {c.canAnswerOutcome && <span className="small" style={{ fontWeight: 700, color: 'var(--amber-ink)' }}>{t('my.didItHelp')}</span>}
            </button>
          ))}
        </div>
      </IonContent>
      <BottomNav active="my" />
    </IonPage>
  );
}
