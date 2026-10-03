import { IonContent, IonIcon, IonPage, useIonRouter } from '@ionic/react';
import { fileTrayFullOutline, libraryOutline, listOutline, settingsOutline, swapHorizontalOutline } from 'ionicons/icons';
import { useTranslation } from 'react-i18next';
import { Avatar } from '../../components/Avatar';
import { BottomNav } from '../../components/BottomNav';
import { CommunitySwitch } from '../../components/CommunitySwitch';
import { LanguageSelect } from '../../components/LanguageSelect';
import { useLive } from '../../live/Live';
import { useSession } from '../../session/Session';

export default function Profile() {
  const { t } = useTranslation();
  const { me, isDoer, isModerator, isAdmin, doerActor } = useSession();
  const { connected } = useLive();
  const router = useIonRouter();

  const link = (path: string, icon: string, label: string) => (
    <button type="button" className="card card--link row" style={{ flexDirection: 'row', gap: 12 }} onClick={() => router.push(path)}>
      <IonIcon icon={icon} style={{ fontSize: 22, color: 'var(--brand)' }} />
      <span style={{ fontWeight: 700 }}>{label}</span>
    </button>
  );

  return (
    <IonPage>
      <IonContent className="page">
        <div className="column pad stack-lg">
          <div className="row" style={{ paddingTop: 8, gap: 14 }}>
            <Avatar name={me?.displayName} size="lg" />
            <div className="stack-sm" style={{ gap: 2 }}>
              <h1 className="h2">{me?.displayName}</h1>
              <span className="small muted">@{me?.login}</span>
            </div>
            <span className="spacer" />
            <span className={`live-dot${connected ? ' live-dot--on' : ''}`}>{connected ? t('live.connected') : t('live.offline')}</span>
          </div>

          <section className="card stack">
            <label className="field">
              <span className="field__label">{t('profile.language')}</span>
              <LanguageSelect className="select" />
            </label>
            <div className="field">
              <span className="field__label">{t('profile.community')}</span>
              <CommunitySwitch />
            </div>
          </section>

          <section className="stack-sm">
            <h2 className="h3">{t('profile.roles')}</h2>
            {(me?.memberships ?? []).map((m, i) => (
              <div key={`${m.community}-${m.role}-${i}`} className="row small">
                <span className="tag tag--sm">{t(`role.${m.role}`)}</span>
                <span>{m.communityName}</span>
                {m.actorName && <span className="muted">· {m.actorName}</span>}
              </div>
            ))}
          </section>

          <section className="stack-sm">
            {isDoer && link('/doer', fileTrayFullOutline, t('profile.inbox', { name: doerActor?.name }))}
            {isModerator && link('/moderation', listOutline, t('profile.moderation'))}
            {isAdmin && link('/admin', settingsOutline, t('profile.setup'))}
            {link('/ideas', libraryOutline, t('profile.ideas'))}
            {link('/signin', swapHorizontalOutline, t('signin.switch'))}
          </section>
        </div>
      </IonContent>
      <BottomNav active="profile" />
    </IonPage>
  );
}
