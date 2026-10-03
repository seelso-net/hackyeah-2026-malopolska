import type { ReactNode } from 'react';
import { IonContent, IonIcon, IonPage, useIonRouter } from '@ionic/react';
import { libraryOutline, listOutline, mapOutline, settingsOutline, swapHorizontalOutline } from 'ionicons/icons';
import { useTranslation } from 'react-i18next';
import { api, unwrap } from '../api/client';
import { useLive } from '../live/Live';
import { useApi, useSession } from '../session/Session';
import { AppLink } from './AppLink';
import { Avatar } from './Avatar';
import { CommunitySwitch } from './CommunitySwitch';
import { LanguageSelect } from './LanguageSelect';
import { Bell } from './Notifications';

type Section = 'queue' | 'playbooks' | 'admin' | 'map';

/** The moderator and admin panel: dark side navigation on desktop, a top bar on phones. */
export function PanelLayout({ active, children }: { active: Section; children: ReactNode; title: string }) {
  const { t } = useTranslation();
  const { me, community, isModerator, isAdmin, roles } = useSession();
  const { connected } = useLive();
  const router = useIonRouter();
  const queue = useApi(['queue', community], () => unwrap(api.GET('/api/moderation/queue', { params: { query: { community } } })), {
    enabled: isModerator,
    staleTime: 15_000,
  });
  const count = queue.data?.counts?.total;

  const link = (section: Section, path: string, icon: string, label: string, badge?: number) => (
    <li>
      <AppLink to={path} direction="root" action="replace" aria-current={active === section ? 'page' : undefined}>
        <IonIcon icon={icon} aria-hidden="true" />
        {label}
        {badge !== undefined && badge > 0 && <span className="count">{badge}</span>}
      </AppLink>
    </li>
  );
  const barLink = (section: Section, path: string, label: string) => (
    <AppLink to={path} direction="root" action="replace" aria-current={active === section ? 'page' : undefined}>
      {label}
    </AppLink>
  );
  const role = isAdmin ? t('role.ADMIN') : isModerator ? t('role.MODERATOR') : roles.length ? t(`role.${roles[0]}`) : '';

  return (
    <IonPage>
      <IonContent className="page" scrollY>
        <div className="panel">
          <nav className="panel-nav" aria-label={t('panel.nav')}>
            <div className="panel-nav__brand">
              <CommunitySwitch variant="dark" />
              <div className="panel-nav__sub">{isAdmin ? t('panel.adminPanel') : t('panel.moderatorPanel')}</div>
            </div>
            <ul>
              {isModerator && link('queue', '/moderation', listOutline, t('panel.queue'), count)}
              {link('map', '/home', mapOutline, t('panel.map'))}
              {link('playbooks', '/playbooks', libraryOutline, t('panel.playbooks'))}
              {isAdmin && link('admin', '/admin', settingsOutline, t('panel.setup'))}
            </ul>
            <div className="panel-nav__me">
              <div className="row">
                <Bell dark />
                <span className={`live-dot${connected ? ' live-dot--on' : ''}`} style={{ color: '#b6c2cc' }}>
                  {connected ? t('live.connected') : t('live.offline')}
                </span>
              </div>
              <div className="panel-nav__who">
                <Avatar name={me?.displayName} />
                <span>
                  <span style={{ display: 'block', fontWeight: 700 }}>{me?.displayName}</span>
                  <span className="role">{role}</span>
                </span>
              </div>
              <div className="row">
                <LanguageSelect short />
                <button type="button" onClick={() => router.push('/signin', 'root', 'replace')} title={t('signin.switch')}>
                  <IonIcon icon={swapHorizontalOutline} aria-hidden="true" /> {t('signin.switchShort')}
                </button>
              </div>
            </div>
          </nav>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="mobile-panel-bar">
              {isModerator && barLink('queue', '/moderation', count ? `${t('panel.queueShort')} · ${count}` : t('panel.queueShort'))}
              {barLink('map', '/home', t('panel.mapShort'))}
              {barLink('playbooks', '/playbooks', t('panel.playbooks'))}
              {isAdmin && barLink('admin', '/admin', t('panel.setupShort'))}
              <span className="spacer" />
              <AppLink to="/signin" direction="root" action="replace" className="mobile-panel-bar__icon" aria-label={t('signin.switch')} title={t('signin.switch')}>
                <IonIcon icon={swapHorizontalOutline} aria-hidden="true" />
              </AppLink>
            </div>
            <main className="panel-main">{children}</main>
          </div>
        </div>
      </IonContent>
    </IonPage>
  );
}
