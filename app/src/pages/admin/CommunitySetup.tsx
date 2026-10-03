import { useEffect, useMemo, useState } from 'react';
import { IonIcon, useIonToast } from '@ionic/react';
import { addOutline, closeOutline, cloudDownloadOutline, cloudUploadOutline } from 'ionicons/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, unwrap, type CommunityConfig } from '../../api/client';
import { PanelLayout } from '../../components/PanelLayout';
import { ErrorState, Loading } from '../../components/States';
import { invalidate, useApi, useSession } from '../../session/Session';

const STATUSES = ['NEW', 'TRIAGED', 'SUGGESTED', 'MATCHED', 'IN_PROGRESS', 'RESOLVED', 'CONFIRMED', 'CLOSED', 'REJECTED'] as const;
type Status = (typeof STATUSES)[number];
type Labels = Record<string, string>;

function pick(labels: Labels | undefined, lang: string) {
  return labels?.[lang] ?? labels?.en ?? Object.values(labels ?? {})[0] ?? '';
}

/** Everything that makes a deployment a city district, a campus or a co-op, edited as data. */
export default function CommunitySetup() {
  const { t } = useTranslation();
  const { lang, me, isAdmin, community } = useSession();
  const queryClient = useQueryClient();
  const [toast] = useIonToast();
  const adminOf = useMemo(
    () => (me?.memberships ?? []).filter((m) => m.role === 'ADMIN').map((m) => ({ slug: m.community!, name: m.communityName! })),
    [me],
  );
  const [slug, setSlug] = useState(community);
  useEffect(() => {
    if (adminOf.length && !adminOf.some((c) => c.slug === slug)) {
      setSlug(adminOf[0].slug);
    }
  }, [adminOf, slug]);

  const config = useApi(['config', slug], () => unwrap(api.GET('/api/admin/communities/{ref}/config', { params: { path: { ref: slug } } })), {
    enabled: isAdmin || adminOf.length > 0,
  });
  const [draft, setDraft] = useState<CommunityConfig | null>(null);
  const [newCode, setNewCode] = useState('');
  const [newLabel, setNewLabel] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => setDraft(config.data ? structuredClone(config.data) : null), [config.data]);

  const dirty = draft !== null && JSON.stringify(draft) !== JSON.stringify(config.data);
  const edit = (change: (c: CommunityConfig) => void) =>
    setDraft((d) => {
      if (!d) {
        return d;
      }
      const next = structuredClone(d);
      change(next);
      return next;
    });

  const save = async (value: CommunityConfig | null = draft) => {
    if (!value) {
      return;
    }
    setBusy(true);
    try {
      await unwrap(api.PUT('/api/admin/communities/{ref}/config', { params: { path: { ref: slug } }, body: value }));
      void invalidate(queryClient, 'config', slug);
      void invalidate(queryClient, 'community');
      void toast({ message: t('setup.saved'), duration: 3000, position: 'top', color: 'dark' });
    } catch (e) {
      void toast({ message: (e as Error).message, duration: 4000, color: 'danger' });
    } finally {
      setBusy(false);
    }
  };

  const exportJson = () => {
    const blob = new Blob([JSON.stringify(config.data, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `${slug}-config.json`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const importJson = async (file?: File) => {
    if (!file) {
      return;
    }
    try {
      const parsed = JSON.parse(await file.text()) as CommunityConfig;
      setDraft(parsed);
      void toast({ message: t('setup.imported'), duration: 3000, position: 'top', color: 'dark' });
    } catch {
      void toast({ message: t('setup.badJson'), duration: 3000, color: 'danger' });
    }
  };

  const workflowLabel = (s: Status) => draft?.workflow?.find((w) => w.status === s);

  return (
    <PanelLayout active="admin" title={t('panel.setup')}>
      <div className="panel-main__head">
        <div className="stack-sm" style={{ flex: '1 1 480px' }}>
          <h1 className="h1">{t('setup.title')}</h1>
          <p className="muted" style={{ margin: 0 }}>{t('setup.lead')}</p>
        </div>
      </div>

      <div className="chips" role="tablist" aria-label={t('setup.communities')} style={{ marginBottom: 18 }}>
        {adminOf.map((c) => (
          <button key={c.slug} type="button" role="tab" className="chip chip--outline" aria-pressed={slug === c.slug} aria-selected={slug === c.slug} onClick={() => setSlug(c.slug)}>
            {c.name}
          </button>
        ))}
      </div>

      {!isAdmin && adminOf.length === 0 && <p className="empty">{t('setup.notAdmin')}</p>}
      {config.isLoading && <Loading />}
      {config.error && <ErrorState error={config.error} onRetry={() => config.refetch()} />}
      {draft && (
        <div className="stack-lg">
          <div className="setup-grid">
            <section className="card stack" style={{ padding: 20 }}>
              <div className="row">
                <h2 className="h3">{t('setup.categories')}</h2>
                <span className="spacer" />
                <span className="small muted">{t('setup.inUse', { count: draft.categories?.length ?? 0 })}</span>
              </div>
              <p className="small muted" style={{ margin: 0 }}>{t('setup.categoriesHint')}</p>
              <div className="row-wrap">
                {(draft.categories ?? []).map((c, i) => (
                  <span key={c.code} className="editable-chip" title={c.code}>
                    {pick(c.labels, lang)}
                    <button type="button" aria-label={t('setup.remove', { name: pick(c.labels, lang) })} onClick={() => edit((d) => d.categories!.splice(i, 1))}>
                      <IonIcon icon={closeOutline} />
                    </button>
                  </span>
                ))}
              </div>
              <form
                className="row-wrap"
                onSubmit={(e) => {
                  e.preventDefault();
                  const code = newCode.trim().toLowerCase();
                  if (!code || !newLabel.trim()) {
                    return;
                  }
                  edit((d) => {
                    d.categories = [...(d.categories ?? []), { code, labels: { [lang]: newLabel.trim(), ...(lang === 'en' ? {} : { en: newLabel.trim() }) } }];
                  });
                  setNewCode('');
                  setNewLabel('');
                }}
              >
                <input className="input" style={{ flex: '1 1 110px' }} placeholder={t('setup.code')} value={newCode} onChange={(e) => setNewCode(e.target.value)} aria-label={t('setup.code')} />
                <input className="input" style={{ flex: '2 1 160px' }} placeholder={t('setup.label')} value={newLabel} onChange={(e) => setNewLabel(e.target.value)} aria-label={t('setup.label')} />
                <button type="submit" className="btn btn--secondary btn--sm">
                  <IonIcon icon={addOutline} aria-hidden="true" /> {t('setup.add')}
                </button>
              </form>
            </section>

            <section className="card stack" style={{ padding: 20 }}>
              <div className="row">
                <h2 className="h3">{t('setup.workflow')}</h2>
                <span className="spacer" />
                <span className="small muted">{t('setup.states', { count: draft.workflow?.length ?? 0 })}</span>
              </div>
              <p className="small muted" style={{ margin: 0 }}>{t('setup.workflowHint')}</p>
              {STATUSES.map((s) => {
                const w = workflowLabel(s);
                return (
                  <div key={s} className="workflow-row">
                    <input
                      type="checkbox"
                      checked={!!w}
                      aria-label={t('setup.showStatus', { status: s })}
                      style={{ width: 20, height: 20, accentColor: 'var(--brand)' }}
                      onChange={(e) =>
                        edit((d) => {
                          const list = d.workflow ?? [];
                          if (e.target.checked) {
                            list.push({ status: s, labels: { [lang]: t(`status.${s}`) } });
                            list.sort((a, b) => STATUSES.indexOf(a.status as Status) - STATUSES.indexOf(b.status as Status));
                          } else {
                            d.workflow = list.filter((x) => x.status !== s);
                            return;
                          }
                          d.workflow = list;
                        })
                      }
                    />
                    <span className="code">{s}</span>
                    <input
                      className="input"
                      style={{ minHeight: 38, padding: '6px 10px' }}
                      disabled={!w}
                      value={w ? w.labels?.[lang] ?? '' : t('setup.hidden')}
                      placeholder={pick(w?.labels, lang)}
                      onChange={(e) =>
                        edit((d) => {
                          const item = d.workflow?.find((x) => x.status === s);
                          if (item) {
                            item.labels = { ...(item.labels ?? {}), [lang]: e.target.value };
                          }
                        })
                      }
                    />
                  </div>
                );
              })}
            </section>

            <section className="card stack" style={{ padding: 20 }}>
              <h2 className="h3">{t('setup.roles')}</h2>
              {(draft.roles ?? []).map((r, i) => (
                <label key={`${r.role}-${i}`} className="field">
                  <span className="field__label">{t(`role.${r.role}`)}</span>
                  <input
                    className="input"
                    value={r.who?.[lang] ?? pick(r.who, lang)}
                    onChange={(e) =>
                      edit((d) => {
                        d.roles![i].who = { ...(d.roles![i].who ?? {}), [lang]: e.target.value };
                      })
                    }
                  />
                </label>
              ))}
            </section>

            <section className="card stack" style={{ padding: 20 }}>
              <h2 className="h3">{t('setup.rules')}</h2>
              {(
                [
                  ['mergeRadiusMeters', 'setup.mergeRadius', 600],
                  ['publicLocationRoundingMeters', 'setup.rounding', 100],
                  ['autoCloseDays', 'setup.autoClose', 14],
                ] as const
              ).map(([key, label, fallback]) => (
                <label key={key} className="field">
                  <span className="field__label">{t(label)}</span>
                  <input
                    className="input"
                    type="number"
                    min={0}
                    value={draft.rules?.[key] ?? fallback}
                    onChange={(e) =>
                      edit((d) => {
                        d.rules = { ...(d.rules ?? {}), [key]: Number(e.target.value) };
                      })
                    }
                  />
                </label>
              ))}
              <label className="switch-row">
                <input
                  type="checkbox"
                  checked={draft.rules?.publicMap ?? true}
                  style={{ width: 22, height: 22, accentColor: 'var(--brand)' }}
                  onChange={(e) =>
                    edit((d) => {
                      d.rules = { ...(d.rules ?? {}), publicMap: e.target.checked };
                    })
                  }
                />
                <span>
                  <strong>{t('setup.publicMap')}</strong>
                  <span className="small muted" style={{ display: 'block' }}>{t('setup.publicMapHint')}</span>
                </span>
              </label>
            </section>
          </div>

          <div className="save-bar">
            <span className="small muted" style={{ flex: '1 1 280px' }}>{t('setup.applies')}</span>
            <label className="btn btn--secondary btn--sm">
              <IonIcon icon={cloudUploadOutline} aria-hidden="true" /> {t('setup.import')}
              <input type="file" accept="application/json,.json" hidden onChange={(e) => importJson(e.target.files?.[0])} />
            </label>
            <button type="button" className="btn btn--secondary btn--sm" onClick={exportJson}>
              <IonIcon icon={cloudDownloadOutline} aria-hidden="true" /> {t('setup.export')}
            </button>
            <button type="button" className="btn btn--secondary btn--sm" disabled={!dirty || busy} onClick={() => setDraft(structuredClone(config.data!))}>
              {t('setup.reset')}
            </button>
            <button type="button" className="btn btn--sm" disabled={!dirty || busy} onClick={() => save()}>
              {t('setup.save')}
            </button>
          </div>
        </div>
      )}
    </PanelLayout>
  );
}
