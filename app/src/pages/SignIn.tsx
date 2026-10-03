import { useState } from 'react';
import { IonContent, IonPage, useIonRouter } from '@ionic/react';
import { useTranslation } from 'react-i18next';
import { Avatar } from '../components/Avatar';
import { LanguageSelect } from '../components/LanguageSelect';
import { useSession } from '../session/Session';

interface DemoUser {
  login: string;
  name: string;
  role: string;
  lang: string;
  home: string;
}

/** The seeded people of the demo (see backend/src/main/resources/db/migration/V2__demo_seed.sql). */
const PEOPLE: { group: string; users: DemoUser[] }[] = [
  {
    group: 'residents',
    users: [
      { login: 'maria', name: 'Maria', role: 'maria', lang: 'PL', home: '/home' },
      { login: 'anna', name: 'Anna K.', role: 'volunteer', lang: 'PL', home: '/home' },
      { login: 'tomasz', name: 'Tomasz W.', role: 'volunteer', lang: 'PL', home: '/home' },
      { login: 'ola', name: 'Ola M.', role: 'volunteer', lang: 'UK', home: '/home' },
    ],
  },
  {
    group: 'moderators',
    users: [
      { login: 'ewa', name: 'Ewa N.', role: 'ewa', lang: 'EN', home: '/moderation' },
      { login: 'zofia', name: 'Zofia R.', role: 'zofia', lang: 'PL', home: '/moderation' },
    ],
  },
  {
    group: 'doers',
    users: [
      { login: 'kasia', name: 'Kasia L.', role: 'kasia', lang: 'PL', home: '/doer' },
      { login: 'jan', name: 'Jan P.', role: 'jan', lang: 'PL', home: '/doer' },
    ],
  },
  {
    group: 'admins',
    users: [{ login: 'piotr', name: 'Piotr K.', role: 'piotr', lang: 'EN', home: '/admin' }],
  },
];

export default function SignIn() {
  const { t } = useTranslation();
  const { signIn, login } = useSession();
  const router = useIonRouter();
  const [other, setOther] = useState('');

  const choose = (u: { login: string; home: string }) => {
    signIn(u.login);
    router.push(u.home, 'root', 'replace');
  };

  return (
    <IonPage>
      <IonContent className="page">
        <div className="column pad stack-lg" style={{ maxWidth: 980 }}>
          <div className="row-wrap" style={{ paddingTop: 12 }}>
            <div className="stack-sm" style={{ flex: '1 1 320px' }}>
              <span className="eyebrow">{t('signin.eyebrow')}</span>
              <h1 className="h1">{t('signin.title')}</h1>
              <p className="muted" style={{ margin: 0 }}>{t('signin.lead')}</p>
            </div>
            <LanguageSelect className="select" style={{ width: "auto" }} />
          </div>

          <section className="card card--tint stack-sm">
            <strong>{t('signin.storyTitle')}</strong>
            <ol style={{ margin: 0, paddingLeft: 20 }} className="stack-sm small">
              <li>{t('signin.story1')}</li>
              <li>{t('signin.story2')}</li>
              <li>{t('signin.story3')}</li>
              <li>{t('signin.story4')}</li>
              <li>{t('signin.story5')}</li>
            </ol>
          </section>

          {PEOPLE.map((g) => (
            <section key={g.group} className="stack">
              <h2 className="h3">{t(`signin.group.${g.group}`)}</h2>
              <div className="who-list">
                {g.users.map((u) => (
                  <button type="button" key={u.login} className="who" onClick={() => choose(u)} aria-current={login === u.login ? 'true' : undefined}>
                    <Avatar name={u.name} size="lg" tone={g.group === 'residents' ? 'amber' : undefined} />
                    <span className="stack-sm" style={{ gap: 2, flex: 1 }}>
                      <span style={{ fontWeight: 700 }}>
                        {u.name} <span className="tag tag--sm">{u.lang}</span>
                      </span>
                      <span className="small muted">{t(`signin.people.${u.role}`)}</span>
                    </span>
                  </button>
                ))}
              </div>
            </section>
          ))}

          <form
            className="row-wrap"
            onSubmit={(e) => {
              e.preventDefault();
              if (other.trim()) {
                choose({ login: other.trim(), home: '/' });
              }
            }}
          >
            <input className="input" style={{ maxWidth: 260 }} placeholder={t('signin.otherPlaceholder')} value={other} onChange={(e) => setOther(e.target.value)} />
            <button type="submit" className="btn btn--secondary">{t('signin.otherButton')}</button>
          </form>
          <p className="tiny muted">{t('signin.note')}</p>
        </div>
      </IonContent>
    </IonPage>
  );
}
