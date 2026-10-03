import { IonIcon } from '@ionic/react';
import { locationOutline } from 'ionicons/icons';
import { useTranslation } from 'react-i18next';
import { api, unwrap } from '../api/client';
import { useApi, useSession } from '../session/Session';

/** One deployment, many communities: switch between a city district, a campus or a co-op. */
export function CommunitySwitch({ variant = 'light' }: { variant?: 'light' | 'dark' }) {
  const { t } = useTranslation();
  const { community, setCommunity } = useSession();
  const list = useApi(['communities'], () => unwrap(api.GET('/api/communities')), { staleTime: 300_000 });
  const items = list.data ?? [];
  const select = (
    <select
      value={community}
      aria-label={t('community.switch')}
      onChange={(e) => setCommunity(e.target.value)}
    >
      {items.length === 0 && <option value={community}>{community}</option>}
      {items.map((c) => (
        <option key={c.slug} value={c.slug}>
          {c.name}
        </option>
      ))}
    </select>
  );
  if (variant === 'dark') {
    return select;
  }
  return (
    <label className="community-switch">
      <IonIcon icon={locationOutline} aria-hidden="true" />
      {select}
    </label>
  );
}
