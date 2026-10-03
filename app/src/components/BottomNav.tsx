import { IonFooter, IonIcon } from '@ionic/react';
import { add, bulbOutline, documentTextOutline, fileTrayFullOutline, mapOutline, personCircleOutline } from 'ionicons/icons';
import { useTranslation } from 'react-i18next';
import { useSession } from '../session/Session';
import { AppLink } from './AppLink';

type Tab = 'map' | 'my' | 'report' | 'ideas' | 'inbox' | 'profile';

/** The resident's (and volunteer's) main navigation, with the report button in the middle. */
export function BottomNav({ active }: { active: Tab }) {
  const { t } = useTranslation();
  const { isDoer } = useSession();
  const item = (tab: Tab, path: string, icon: string, label: string) => (
    <AppLink to={path} direction="root" action="replace" aria-current={active === tab ? 'page' : undefined}>
      <IonIcon icon={icon} aria-hidden="true" />
      {label}
    </AppLink>
  );
  return (
    <IonFooter className="ion-no-border">
      <nav className="bottom-nav" aria-label={t('nav.main')}>
        <div className="bottom-nav__inner">
          {item('map', '/home', mapOutline, t('nav.map'))}
          {item('my', '/my', documentTextOutline, t('nav.my'))}
          <AppLink to="/report/new" className="fab">
            <span className="fab__circle">
              <IonIcon icon={add} aria-hidden="true" />
            </span>
            {t('nav.report')}
          </AppLink>
          {isDoer ? item('inbox', '/doer', fileTrayFullOutline, t('nav.inbox')) : item('ideas', '/ideas', bulbOutline, t('nav.ideas'))}
          {item('profile', '/profile', personCircleOutline, t('nav.profile'))}
        </div>
      </nav>
    </IonFooter>
  );
}
