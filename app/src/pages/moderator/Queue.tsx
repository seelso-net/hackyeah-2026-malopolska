import { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { IonIcon, IonSpinner, useIonRouter, useIonToast } from '@ionic/react';
import { alertCircleOutline, libraryOutline, peopleOutline } from 'ionicons/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, unwrap, type CandidateView, type QueueItem } from '../../api/client';
import { MapView, type Pin } from '../../components/MapView';
import { PanelLayout } from '../../components/PanelLayout';
import { ErrorState, Loading } from '../../components/States';
import { ActorKindTag, AiStatusTag, KindTag, UrgencyLabel } from '../../components/Tags';
import { Timeline } from '../../components/Timeline';
import { Translated } from '../../components/Translated';
import { distance, metersBetween, timeAgo } from '../../lib/format';
import { invalidate, useApi, useSession } from '../../session/Session';

type Filter = 'all' | 'NEED' | 'IDEA' | 'urgent';

export default function ModeratorQueue() {
  const { t } = useTranslation();
  const { community, lang, isModerator, meLoading } = useSession();
  const location = useLocation();
  const router = useIonRouter();
  const [filter, setFilter] = useState<Filter>('all');
  const selected = new URLSearchParams(location.search).get('case');

  const queue = useApi(['queue', community], () => unwrap(api.GET('/api/moderation/queue', { params: { query: { community } } })), {
    enabled: isModerator,
  });
  const items = queue.data?.items ?? [];
  const urgent = (i: QueueItem) => i.urgency === 'HIGH' || i.urgency === 'EMERGENCY' || i.safetyConcern;
  const shown = items.filter((i) => (filter === 'all' ? true : filter === 'urgent' ? urgent(i) : i.kind === filter));
  const current = selected ?? shown[0]?.id;
  const select = (id?: string) => router.push(id ? `/moderation?case=${id}` : '/moderation', 'none', 'replace');

  if (!meLoading && !isModerator) {
    return (
      <PanelLayout active="queue" title={t('queue.title')}>
        <div className="empty">{t('queue.notModerator')}</div>
      </PanelLayout>
    );
  }

  const count = (f: Filter) => items.filter((i) => (f === 'all' ? true : f === 'urgent' ? urgent(i) : i.kind === f)).length;

  return (
    <PanelLayout active="queue" title={t('queue.title')}>
      <div className="panel-main__head">
        <div className="stack-sm" style={{ flex: '1 1 400px' }}>
          <h1 className="h1">{t('queue.title')}</h1>
          <p className="muted" style={{ margin: 0 }}>{t('queue.lead', { count: items.length })}</p>
        </div>
      </div>
      <div className="chips" role="group" aria-label={t('queue.filter')} style={{ marginBottom: 18 }}>
        {(['all', 'NEED', 'IDEA', 'urgent'] as Filter[]).map((f) => (
          <button key={f} type="button" className="chip chip--outline" aria-pressed={filter === f} onClick={() => setFilter(f)} style={f === 'urgent' && filter !== f ? { color: 'var(--danger)' } : undefined}>
            {f === 'urgent' && <span className="chip__dot" style={{ width: 8, height: 8 }} />}
            {t(`queue.filters.${f}`)} · {count(f)}
          </button>
        ))}
      </div>
      {queue.isLoading && <Loading />}
      {queue.error && <ErrorState error={queue.error} onRetry={() => queue.refetch()} />}
      {queue.data && (
        <div className="split">
          <ul className="split__list" aria-label={t('queue.cases')}>
            {shown.length === 0 && <li className="empty">{t('queue.empty')}</li>}
            {shown.map((i) => (
              <li key={i.id}>
                <button type="button" className="queue-item" aria-current={i.id === current} onClick={() => select(i.id)}>
                  <span className="row">
                    <KindTag kind={i.kind} small />
                    <span className="small muted">{i.categoryLabel}</span>
                    <span className="spacer" />
                    {i.safetyConcern && <span className="tag tag--sm tag--danger">{t('queue.safety')}</span>}
                    {i.kind !== 'IDEA' && <UrgencyLabel urgency={i.urgency} />}
                  </span>
                  <span className="queue-item__title">{i.title?.text}</span>
                  <span className="row small muted">
                    {i.kind === 'IDEA'
                      ? t('queue.supporters', { count: i.supporterCount ?? 0 })
                      : t('queue.reports', { count: i.reportCount ?? 0 })}{' '}
                    · {timeAgo(i.createdAt, lang)}
                    <span className="spacer" />
                    <AiStatusTag status={i.aiStatus} />
                  </span>
                </button>
              </li>
            ))}
          </ul>
          {current ? (
            <CaseDecision
              key={current}
              id={current}
              onDecided={() => {
                const next = shown.find((i) => i.id !== current);
                select(next?.id);
              }}
            />
          ) : (
            <div className="split__detail empty">{t('queue.nothing')}</div>
          )}
        </div>
      )}
    </PanelLayout>
  );
}

function CaseDecision({ id, onDecided }: { id: string; onDecided: () => void }) {
  const { t } = useTranslation();
  const { lang } = useSession();
  const router = useIonRouter();
  const queryClient = useQueryClient();
  const [toast] = useIonToast();
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [version, setVersion] = useState<string>('');
  const [allReports, setAllReports] = useState(false);
  const [busy, setBusy] = useState(false);

  const detail = useApi(['moderation-case', id], () => unwrap(api.GET('/api/moderation/cases/{id}', { params: { path: { id } } })));
  const c = detail.data;
  const s = c?.suggestion;
  const slug = c?.community?.slug ?? '';
  const config = useApi(['community', slug], () => unwrap(api.GET('/api/communities/{slug}', { params: { path: { slug } } })), {
    enabled: !!slug,
  });

  useEffect(() => {
    if (s) {
      setChosen(new Set((s.doers ?? []).filter((d) => d.selected).map((d) => d.actorId!)));
      setVersion(s.playbook?.versionId ?? '');
    }
  }, [s?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Residents who pressed "I can help" after the AI's suggestion: offered as extra doers.
  const offers: CandidateView[] = useMemo(() => {
    const known = new Set((s?.doers ?? []).map((d) => d.actorId));
    const list: CandidateView[] = [];
    for (const e of c?.timeline ?? []) {
      const d = (e.data ?? {}) as Record<string, string>;
      if (e.type === 'HELP_OFFERED' && d.actorId && !known.has(d.actorId)) {
        known.add(d.actorId);
        list.push({ actorId: d.actorId, name: d.actorName, kind: 'VOLUNTEER', reason: d.note ? { text: `“${d.note}”` } : undefined, selected: false });
      }
    }
    return list;
  }, [c?.timeline, s?.doers]);

  if (detail.isLoading) {
    return <div className="split__detail"><Loading /></div>;
  }
  if (detail.error || !c) {
    return <div className="split__detail"><ErrorState error={detail.error ?? 'Not found'} onRetry={() => detail.refetch()} /></div>;
  }

  const reports = c.reports ?? [];
  const shownReports = allReports ? reports : reports.slice(0, 2);
  const centre = { lat: c.lat, lng: c.lng };
  const spread = Math.max(0, ...reports.map((r) => metersBetween(centre, r) ?? 0));
  const pins: Pin[] = [
    ...reports.filter((r) => r.lat != null && r.lng != null).map((r) => ({ id: r.id!, lat: r.lat!, lng: r.lng!, kind: 'report' as const })),
    ...(c.lat != null && c.lng != null ? [{ id: 'case', lat: c.lat, lng: c.lng, kind: 'need' as const, label: String(c.reportCount ?? '') }] : []),
  ];
  const unsafe = reports.some((r) => r.safetyConcern);
  const waiting = c.status === 'NEW' || c.status === 'TRIAGED' || c.status === 'SUGGESTED';
  const playbookOptions = [
    ...(s?.playbook ? [{ versionId: s.playbook.versionId!, title: s.playbook.title?.text, origin: s.playbook.origin }] : []),
    ...(s?.alternatives ?? []).map((a) => ({ versionId: a.versionId!, title: a.title?.text, origin: a.origin })),
  ];
  const residents = (c.reportCount ?? 0) + (c.supporterCount ?? 0);

  const approve = async () => {
    if (!s?.id) {
      return;
    }
    setBusy(true);
    try {
      await unwrap(
        api.POST('/api/moderation/suggestions/{id}/approve', {
          params: { path: { id: s.id } },
          body: { actorIds: [...chosen], playbookVersionId: version || undefined },
        }),
      );
      void toast({ message: t('queue.approved', { count: chosen.size, residents }), duration: 3500, position: 'top', color: 'dark' });
      void invalidate(queryClient, 'queue');
      void invalidate(queryClient, 'moderation-case', id);
      onDecided();
    } catch (e) {
      void toast({ message: (e as Error).message, duration: 3500, color: 'danger' });
    } finally {
      setBusy(false);
    }
  };

  const toggle = (actorId: string) =>
    setChosen((set) => {
      const next = new Set(set);
      if (next.has(actorId)) {
        next.delete(actorId);
      } else {
        next.add(actorId);
      }
      return next;
    });

  return (
    <section className="split__detail" aria-label={c.title?.text}>
      <div className="stack-sm">
        <div className="row-wrap">
          <KindTag kind={c.kind} />
          <span className="tag">{c.categoryLabel}</span>
          {c.kind !== 'IDEA' && <span className="tag">{t(`urgency.${c.urgency}`)} {t('queue.urgencyWord')}</span>}
          <span className="small muted">{t('queue.caseMeta', { number: c.number, when: timeAgo(c.createdAt, lang) })}</span>
        </div>
        <h2 className="h2" style={{ fontSize: 26 }}>{c.title?.text}</h2>
        <Translated value={c.summary} as="p" className="muted" />
      </div>

      {unsafe && (
        <div className="notice notice--danger">
          <IonIcon icon={alertCircleOutline} aria-hidden="true" /> {t('queue.safetyNotice')}
        </div>
      )}

      <div className="row-wrap" style={{ alignItems: 'flex-start', gap: 20 }}>
        <div className="stack" style={{ flex: '1 1 300px', minWidth: 0 }}>
          <span className="eyebrow">{t('queue.residentsSaid', { count: reports.length })}</span>
          {shownReports.map((r) => (
            <blockquote key={r.id} className="quote">
              <Translated value={r.text} as="p" />
              <p className="tiny muted" style={{ marginTop: 6 }}>
                {r.anonymous ? t('queue.anonymous') : r.author ?? t('queue.resident')} · {t(`queue.mode.${r.inputMode ?? 'TEXT'}`)} · {timeAgo(r.createdAt, lang)}
                {r.safetyConcern && <strong style={{ color: 'var(--danger)' }}> · {t('queue.safety')}</strong>}
              </p>
            </blockquote>
          ))}
          {reports.length > 2 && (
            <button type="button" className="btn btn--ghost btn--sm" style={{ alignSelf: 'flex-start' }} onClick={() => setAllReports(!allReports)}>
              {allReports ? t('queue.fewerReports') : t('queue.allReports', { count: reports.length })}
            </button>
          )}
        </div>
        <figure style={{ flex: '1 1 240px', margin: 0, minWidth: 0 }} className="stack-sm">
          <MapView className="map map--panel" pins={pins} label={t('queue.mapLabel')} fitPadding={30} />
          <figcaption className="small muted">
            {t('queue.spread', { count: reports.length, distance: distance(Math.max(spread, 50), lang), area: c.areaLabel ?? '' })}
          </figcaption>
        </figure>
      </div>

      {waiting && !s && (
        <div className="card--quiet card row" style={{ flexDirection: 'row', gap: 12 }}>
          <IonSpinner name="dots" color="primary" />
          <span>{t('queue.aiWorking')}</span>
        </div>
      )}

      {waiting && s && (
        <section className="card card--quiet stack" style={{ gap: 16 }} aria-label={t('queue.aiSuggestion')}>
          <div className="row-wrap">
            <span className="tag tag--dark">{t('queue.aiSuggestion')}</span>
            <span className="muted">{t('queue.checkFirst')}</span>
            <span className="spacer" />
            <span className="tiny muted">{t('queue.engine', { engine: s.engine })}</span>
          </div>

          {s.playbook || playbookOptions.length > 0 ? (
            <div className="playbook-chip">
              <span className="playbook-chip__icon">
                <IonIcon icon={libraryOutline} />
              </span>
              <div style={{ flex: '1 1 240px', minWidth: 0 }}>
                <div className="eyebrow">{t('queue.playbook')}</div>
                <select className="select" style={{ border: 0, padding: 0, minHeight: 32, fontWeight: 700, fontSize: 18 }} value={version} onChange={(e) => setVersion(e.target.value)} aria-label={t('queue.playbook')}>
                  {!s.playbook && <option value="">{t('queue.noPlaybook')}</option>}
                  {playbookOptions.map((p, i) => (
                    <option key={p.versionId} value={p.versionId}>
                      {p.title}
                      {i === 0 && s.playbook ? '' : ` (${t('queue.alternative')})`}
                    </option>
                  ))}
                </select>
                {s.playbook && version === s.playbook.versionId && (
                  <div className="small muted">
                    {t('queue.playbookMeta', { origin: s.playbook.origin, steps: s.playbook.steps?.length ?? 0 })}
                  </div>
                )}
              </div>
              {s.playbook && (
                <button type="button" className="btn btn--secondary btn--sm" onClick={() => router.push(`/playbooks/${s.playbook!.slug}`)}>
                  {t('queue.openPlaybook')}
                </button>
              )}
            </div>
          ) : null}

          {s.reason?.text && (
            <p style={{ margin: 0, color: 'var(--ink-2)' }}>
              <strong style={{ color: 'var(--ink)' }}>{t('queue.why')}</strong> {s.reason.text}
            </p>
          )}

          <fieldset style={{ margin: 0, padding: 0, border: 0 }} className="stack-sm">
            <legend className="eyebrow" style={{ marginBottom: 8 }}>{t('queue.whoActs')}</legend>
            {[...(s.doers ?? []), ...offers].map((d) => (
              <label key={d.actorId} className={`check${chosen.has(d.actorId!) ? '' : ' check--off'}`}>
                <input type="checkbox" checked={chosen.has(d.actorId!)} onChange={() => toggle(d.actorId!)} />
                <span className="stack-sm" style={{ gap: 2, flex: 1, minWidth: 0 }}>
                  <span className="row-wrap">
                    <span className="check__name">{d.name}</span>
                    <ActorKindTag kind={d.kind} />
                    {offers.includes(d) && <span className="tag tag--sm tag--ok">{t('queue.offeredHelp')}</span>}
                  </span>
                  {d.reason?.text && <span className="small muted">{d.reason.text}</span>}
                </span>
              </label>
            ))}
          </fieldset>

          <div className="row-wrap">
            <button type="button" className="btn btn--lg" disabled={busy || chosen.size === 0} onClick={approve}>
              {busy ? t('queue.approving') : t('queue.approve')}
            </button>
            <span className="small muted">
              <IonIcon icon={peopleOutline} aria-hidden="true" /> {t('queue.residentsNotified', { count: residents })}
            </span>
          </div>
        </section>
      )}

      {!waiting && (
        <section className="stack">
          <div className="notice notice--info">{t('queue.alreadyDecided', { status: c.statusLabel })}</div>
          {(c.helpers ?? []).length > 0 && (
            <div className="row-wrap">
              {c.helpers!.map((h) => (
                <span key={h.assignmentId} className="tag">
                  {h.name} · {t(`helperStatus.${h.status}`)}
                </span>
              ))}
            </div>
          )}
        </section>
      )}

      <details>
        <summary className="btn btn--ghost btn--sm" style={{ display: 'inline-flex' }}>{t('queue.history')}</summary>
        <div style={{ marginTop: 12 }}>
          <Timeline items={c.timeline ?? []} config={config.data} />
        </div>
      </details>
    </section>
  );
}
