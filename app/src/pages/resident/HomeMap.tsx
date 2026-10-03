import { useEffect, useMemo, useState } from 'react';
import { IonContent, IonPage, useIonAlert, useIonRouter, useIonToast } from '@ionic/react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, unwrap, type CaseCard, type MapInitiative } from '../../api/client';
import { AppLink } from '../../components/AppLink';
import { BottomNav } from '../../components/BottomNav';
import { CommunitySwitch } from '../../components/CommunitySwitch';
import { MapView, type Pin } from '../../components/MapView';
import { Bell } from '../../components/Notifications';
import { ErrorState, Loading } from '../../components/States';
import { KindTag } from '../../components/Tags';
import { distance, metersBetween } from '../../lib/format';
import { invalidate, useApi, useSession } from '../../session/Session';

type Filter = 'all' | 'NEED' | 'IDEA' | 'initiatives';

interface Point {
  lat: number;
  lng: number;
}

/** Where "you" are: the phone's position when it is in this community, otherwise the community's centre. */
function useHere(centre: Point | null): Point | null {
  const [gps, setGps] = useState<Point | null>(null);
  useEffect(() => {
    navigator.geolocation?.getCurrentPosition(
      (p) => setGps({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => setGps(null),
      { maximumAge: 120_000, timeout: 6_000 },
    );
  }, []);
  if (gps && centre && (metersBetween(gps, centre) ?? Infinity) < 15_000) {
    return gps;
  }
  return centre;
}

export function centreOf(points: { lat?: number; lng?: number }[]): Point | null {
  const valid = points.filter((p) => p.lat != null && p.lng != null) as Point[];
  if (valid.length === 0) {
    return null;
  }
  return {
    lat: valid.reduce((s, p) => s + p.lat, 0) / valid.length,
    lng: valid.reduce((s, p) => s + p.lng, 0) / valid.length,
  };
}

export default function HomeMap() {
  const { t } = useTranslation();
  const { community, lang } = useSession();
  const router = useIonRouter();
  const queryClient = useQueryClient();
  const [toast] = useIonToast();
  const [ask] = useIonAlert();
  const [filter, setFilter] = useState<Filter>('all');

  const map = useApi(['map', community], () => unwrap(api.GET('/api/map', { params: { query: { community } } })));
  const mine = useApi(['my-cases'], () => unwrap(api.GET('/api/me/cases')));

  const cases = useMemo(() => (map.data?.cases ?? []).filter((c) => c.status !== 'CLOSED' && c.status !== 'REJECTED'), [map.data]);
  const initiatives = map.data?.initiatives ?? [];
  const centre = useMemo(() => centreOf([...cases, ...initiatives]), [cases, initiatives]);
  const here = useHere(centre);
  const myCases = useMemo(() => new Map((mine.data ?? []).map((c) => [c.id, c.role])), [mine.data]);

  const needs = cases.filter((c) => c.kind === 'NEED');
  const ideas = cases.filter((c) => c.kind === 'IDEA');
  const shownCases = filter === 'all' ? cases : filter === 'initiatives' ? [] : cases.filter((c) => c.kind === filter);
  const shownInitiatives = filter === 'all' || filter === 'initiatives' ? initiatives : [];

  type Entry = { type: 'case'; item: CaseCard; d?: number } | { type: 'initiative'; item: MapInitiative; d?: number };
  const entries: Entry[] = [
    ...shownCases.map((c) => ({ type: 'case' as const, item: c, d: metersBetween(here, c) })),
    ...shownInitiatives.map((i) => ({ type: 'initiative' as const, item: i, d: metersBetween(here, i) })),
  ].sort((a, b) => (a.d ?? 0) - (b.d ?? 0));

  const pins: Pin[] = [
    ...shownCases
      .filter((c) => c.lat != null && c.lng != null)
      .map((c) => ({
        id: c.id!,
        lat: c.lat!,
        lng: c.lng!,
        kind: c.kind === 'IDEA' ? ('idea' as const) : ('need' as const),
        label: String(c.kind === 'IDEA' ? c.supporterCount ?? 0 : c.reportCount ?? 0),
        big: myCases.has(c.id!),
        title: c.title?.text,
        onClick: () => router.push(`/case/${c.id}`),
      })),
    ...shownInitiatives
      .filter((i) => i.lat != null && i.lng != null)
      .map((i) => ({ id: i.id!, lat: i.lat!, lng: i.lng!, kind: 'initiative' as const, label: '✦', title: i.title?.text })),
    ...(here ? [{ id: 'me', lat: here.lat, lng: here.lng, kind: 'me' as const, title: t('home.you') }] : []),
  ];

  const support = async (c: CaseCard) => {
    try {
      await unwrap(api.POST('/api/cases/{id}/support', { params: { path: { id: c.id! } } }));
      void invalidate(queryClient, 'map');
      void invalidate(queryClient, 'my-cases');
      void toast({ message: t('home.supported'), duration: 2500, position: 'top', color: 'dark' });
    } catch (e) {
      void toast({ message: (e as Error).message, duration: 3000, color: 'danger' });
    }
  };

  const offer = (c: CaseCard) =>
    ask({
      header: t('home.offerTitle'),
      message: t('home.offerMessage'),
      inputs: [{ name: 'note', type: 'textarea', placeholder: t('home.offerPlaceholder') }],
      buttons: [
        { text: t('common.cancel'), role: 'cancel' },
        {
          text: t('home.offerSend'),
          handler: async (values: { note?: string }) => {
            try {
              await unwrap(
                api.POST('/api/cases/{id}/help-offers', {
                  params: { path: { id: c.id! } },
                  body: { note: values?.note ?? '' },
                }),
              );
              void invalidate(queryClient, 'my-cases');
              void toast({ message: t('home.offerThanks'), duration: 3000, position: 'top', color: 'dark' });
            } catch (e) {
              void toast({ message: (e as Error).message, duration: 3000, color: 'danger' });
            }
          },
        },
      ],
    });

  return (
    <IonPage>
      <IonContent className="page">
        <div className="column" style={{ maxWidth: 720 }}>
          <header className="home-header">
            <div className="row">
              <CommunitySwitch />
              <span className="spacer" />
              <Bell flat />
            </div>
            <h1 className="h1" style={{ marginTop: 2 }}>{t('home.title')}</h1>
            <div className="chips" role="group" aria-label={t('home.filter')} style={{ marginTop: 12 }}>
              <button type="button" className="chip" aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>
                {t('home.all')}
              </button>
              <button type="button" className="chip" aria-pressed={filter === 'NEED'} onClick={() => setFilter('NEED')}>
                <span className="chip__dot" style={{ color: 'var(--amber)' }} />
                {t('home.needs')} · {needs.length}
              </button>
              <button type="button" className="chip" aria-pressed={filter === 'IDEA'} onClick={() => setFilter('IDEA')}>
                <span className="chip__dot" style={{ color: '#3b4752' }} />
                {t('home.ideas')} · {ideas.length}
              </button>
              <button type="button" className="chip" aria-pressed={filter === 'initiatives'} onClick={() => setFilter('initiatives')}>
                <span className="chip__dot" style={{ color: 'var(--brand)' }} />
                {t('home.initiatives')} · {initiatives.length}
              </button>
            </div>
          </header>

          {map.isLoading && <Loading />}
          {map.error && <ErrorState error={map.error} onRetry={() => map.refetch()} />}
          {map.data && (
            <>
              <MapView pins={pins} label={t('home.mapLabel', { name: map.data.community?.name })} />
              <section className="sheet" aria-label={t('home.nearYou')}>
                <div className="sheet__grabber" />
                <div className="section-title">
                  <h2>{t('home.nearYou')}</h2>
                  <span className="small muted">
                    {t('home.counts', { needs: needs.length + ideas.length, initiatives: initiatives.length })}
                  </span>
                </div>
                {entries.length === 0 && <p className="empty">{t('home.empty')}</p>}
                {entries.map((e) =>
                  e.type === 'case' ? (
                    <article key={e.item.id} className={`card${myCases.has(e.item.id!) ? ' card--highlight' : ''}`}>
                      <div className="card__head">
                        <KindTag kind={e.item.kind} />
                        <span className="card__sub">{e.item.categoryLabel}</span>
                        <span className="card__dist">{distance(e.d, lang)}</span>
                      </div>
                      <h3 className="card__title">
                        <AppLink to={`/case/${e.item.id}`} style={{ color: 'inherit', textDecoration: 'none' }}>
                          {e.item.title?.text}
                        </AppLink>
                      </h3>
                      <p className="card__meta">
                        {e.item.kind === 'IDEA'
                          ? t('home.supporters', { count: e.item.supporterCount ?? 0 })
                          : t('home.reported', { count: e.item.reportCount ?? 0 })}{' '}
                        · {e.item.statusLabel}
                      </p>
                      <div className="card__actions">
                        {myCases.has(e.item.id!) ? (
                          <button type="button" className="btn btn--soft btn--sm" onClick={() => router.push(`/case/${e.item.id}`)}>
                            {t(`home.role.${myCases.get(e.item.id!)}`)} · {t('home.open')}
                          </button>
                        ) : (
                          <>
                            <button type="button" className="btn btn--secondary btn--sm" onClick={() => support(e.item)}>
                              {e.item.kind === 'IDEA' ? t('home.support') : t('home.meToo')}
                            </button>
                            <button type="button" className="btn btn--soft btn--sm" onClick={() => offer(e.item)}>
                              {t('home.canHelp')}
                            </button>
                          </>
                        )}
                      </div>
                    </article>
                  ) : (
                    <article key={e.item.id} className="card">
                      <div className="card__head">
                        <KindTag kind="INITIATIVE" />
                        <span className="card__sub">{e.item.schedule?.text}</span>
                        <span className="card__dist">{distance(e.d, lang)}</span>
                      </div>
                      <h3 className="card__title">{e.item.title?.text}</h3>
                      <p className="card__meta">
                        {e.item.actorName ? t('home.runBy', { name: e.item.actorName }) : e.item.description?.text}
                      </p>
                    </article>
                  ),
                )}
                <button type="button" className="btn btn--ghost" onClick={() => router.push('/ideas')}>
                  {t('home.ideasLibrary')}
                </button>
              </section>
            </>
          )}
        </div>
      </IonContent>
      <BottomNav active="map" />
    </IonPage>
  );
}
