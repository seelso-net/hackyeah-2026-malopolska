import { useState } from 'react';
import { IonContent, IonIcon, IonPage, useIonAlert, useIonToast } from '@ionic/react';
import { cameraOutline, checkmarkCircle, closeOutline, ellipseOutline, sendOutline } from 'ionicons/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, postForm, unwrap, type AssignmentView } from '../../api/client';
import { AppLink } from '../../components/AppLink';
import { Avatar } from '../../components/Avatar';
import { BottomNav } from '../../components/BottomNav';
import { Bell } from '../../components/Notifications';
import { ErrorState, Loading } from '../../components/States';
import { KindTag } from '../../components/Tags';
import { Translated } from '../../components/Translated';
import { timeAgo } from '../../lib/format';
import { invalidate, useApi, useSession } from '../../session/Session';

export default function DoerInbox() {
  const { t } = useTranslation();
  const { doerActor, me, isDoer, meLoading } = useSession();
  const inbox = useApi(['assignments', 'open'], () => unwrap(api.GET('/api/doer/assignments')));
  const done = useApi(['assignments', 'done'], () => unwrap(api.GET('/api/doer/assignments', { params: { query: { status: 'DONE' } } })));
  const list = inbox.data ?? [];
  const offered = list.filter((a) => a.status === 'OFFERED');
  const active = list.filter((a) => a.status === 'ACCEPTED');
  const actorName = doerActor?.name ?? list[0]?.actor?.name ?? me?.displayName;
  const communityName = list[0]?.case?.community?.name;

  return (
    <IonPage>
      <IonContent className="page">
        <div className="column pad stack-lg">
          <div className="row" style={{ paddingTop: 8, gap: 12 }}>
            <Avatar name={actorName} size="lg" />
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ fontWeight: 700, fontSize: 17 }}>{actorName}</div>
              <div className="small muted">{t('doer.subtitle', { community: communityName ?? '' })}</div>
            </div>
            <Bell flat />
          </div>
          <h1 className="h1">{t('doer.title')}</h1>

          {!meLoading && !isDoer && list.length === 0 && <p className="empty">{t('doer.notDoer')}</p>}
          {inbox.isLoading && <Loading />}
          {inbox.error && <ErrorState error={inbox.error} onRetry={() => inbox.refetch()} />}

          {inbox.data && (
            <>
              <section className="stack">
                <div className="section-title">
                  <h2>{t('doer.newForYou', { count: offered.length })}</h2>
                </div>
                {offered.length === 0 && <p className="small muted" style={{ margin: 0 }}>{t('doer.noNew')}</p>}
                {offered.map((a) => (
                  <OfferCard key={a.id} a={a} />
                ))}
              </section>

              <section className="stack">
                <div className="section-title">
                  <h2>{t('doer.active', { count: active.length })}</h2>
                </div>
                {active.length === 0 && <p className="small muted" style={{ margin: 0 }}>{t('doer.noActive')}</p>}
                {active.map((a) => (
                  <ActiveCard key={a.id} a={a} />
                ))}
              </section>
            </>
          )}

          {(done.data ?? []).length > 0 && (
            <section className="stack-sm">
              <div className="section-title">
                <h2>{t('doer.done', { count: done.data!.length })}</h2>
              </div>
              {done.data!.map((a) => (
                <div key={a.id} className="card">
                  <div className="card__head">
                    <IonIcon icon={checkmarkCircle} style={{ color: 'var(--brand)', fontSize: 20 }} />
                    <span className="small muted">{a.case?.number}</span>
                    <span className="spacer" />
                    <span className="tag tag--sm">{a.case?.statusLabel}</span>
                  </div>
                  <span style={{ fontWeight: 700 }}>{a.case?.title?.text}</span>
                </div>
              ))}
            </section>
          )}
        </div>
      </IonContent>
      <BottomNav active="inbox" />
    </IonPage>
  );
}

function OfferCard({ a }: { a: AssignmentView }) {
  const { t } = useTranslation();
  const { lang } = useSession();
  const queryClient = useQueryClient();
  const [toast] = useIonToast();
  const [ask] = useIonAlert();
  const [busy, setBusy] = useState(false);
  const c = a.case ?? {};
  const mine = (a.tasks ?? []).filter((task) => task.assignmentId === a.id).length;

  const accept = async () => {
    setBusy(true);
    try {
      await unwrap(api.POST('/api/assignments/{id}/accept', { params: { path: { id: a.id! } } }));
      void invalidate(queryClient, 'assignments');
      void toast({ message: t('doer.accepted'), duration: 2500, position: 'top', color: 'dark' });
    } catch (e) {
      void toast({ message: (e as Error).message, duration: 3000, color: 'danger' });
    } finally {
      setBusy(false);
    }
  };

  const passOn = (redirect: boolean) =>
    ask({
      header: redirect ? t('doer.redirectTitle') : t('doer.declineTitle'),
      message: redirect ? t('doer.redirectMessage') : t('doer.declineMessage'),
      inputs: [{ name: 'note', type: 'textarea', placeholder: t('doer.notePlaceholder') }],
      buttons: [
        { text: t('common.cancel'), role: 'cancel' },
        {
          text: redirect ? t('doer.redirect') : t('doer.decline'),
          handler: async (values: { note?: string }) => {
            try {
              const path = { params: { path: { id: a.id! } }, body: { note: values?.note ?? '' } };
              await unwrap(redirect ? api.POST('/api/assignments/{id}/redirect', path) : api.POST('/api/assignments/{id}/decline', path));
              void invalidate(queryClient, 'assignments');
              void toast({ message: t('doer.passedOn'), duration: 2500, position: 'top', color: 'dark' });
            } catch (e) {
              void toast({ message: (e as Error).message, duration: 3000, color: 'danger' });
            }
          },
        },
      ],
    });

  return (
    <article className="card card--highlight" style={{ gap: 8 }}>
      <span className="tiny muted">{t('doer.from', { name: a.assignedBy ?? t('role.MODERATOR'), when: timeAgo(a.createdAt, lang) })}</span>
      <div className="card__head">
        <KindTag kind={c.kind} small />
        <span className="small muted">{c.categoryLabel}</span>
        <span className="spacer" />
        <span className="small muted">{c.number}</span>
      </div>
      <h3 className="card__title">{c.title?.text}</h3>
      <p className="card__meta">
        {t('doer.reportsMeta', { count: c.reportCount ?? 0, area: c.areaLabel ?? c.community?.name ?? '' })}
      </p>
      <Translated value={a.summary} as="p" className="small" />
      {a.playbook && <p className="small" style={{ margin: 0 }}>{t('doer.playbookSteps', { title: a.playbook.title?.text, steps: a.tasks?.length ?? 0, mine })}</p>}
      {(a.team ?? []).length > 0 && <p className="tiny muted" style={{ margin: 0 }}>{t('doer.with', { names: a.team!.map((h) => h.name).join(', ') })}</p>}
      <div className="card__actions">
        <button type="button" className="btn btn--sm" disabled={busy} onClick={accept}>
          {t('doer.accept')}
        </button>
        <button type="button" className="btn btn--secondary btn--sm" disabled={busy} onClick={() => passOn(true)}>
          {t('doer.redirect')}
        </button>
      </div>
      <button type="button" className="btn btn--ghost btn--sm" style={{ alignSelf: 'flex-start', color: 'var(--danger)' }} onClick={() => passOn(false)}>
        {t('doer.decline')}
      </button>
    </article>
  );
}

function ActiveCard({ a }: { a: AssignmentView }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [toast] = useIonToast();
  const [text, setText] = useState('');
  const [resolve, setResolve] = useState(false);
  const [photos, setPhotos] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [showOthers, setShowOthers] = useState(false);
  const [ticks, setTicks] = useState<Record<string, boolean>>({});
  const c = a.case ?? {};
  const tasks = a.tasks ?? [];
  const mine = tasks.filter((task) => task.assignmentId === a.id);
  const others = tasks.filter((task) => task.assignmentId !== a.id);
  const residents = (c.reportCount ?? 0) + (c.supporterCount ?? 0);

  const tick = async (taskId: string, done: boolean) => {
    setTicks((t0) => ({ ...t0, [taskId]: done }));
    try {
      await unwrap(api.PATCH('/api/tasks/{id}', { params: { path: { id: taskId } }, body: { done } }));
      void invalidate(queryClient, 'assignments');
    } catch (e) {
      setTicks((t0) => ({ ...t0, [taskId]: !done }));
      void toast({ message: (e as Error).message, duration: 3000, color: 'danger' });
    }
  };
  const isDone = (taskId: string, server?: boolean) => ticks[taskId] ?? !!server;

  const send = async () => {
    if (!text.trim() && photos.length === 0 && !resolve) {
      return;
    }
    setBusy(true);
    try {
      const form = new FormData();
      form.set('text', text.trim());
      form.set('resolve', String(resolve));
      photos.forEach((p) => form.append('photo', p, p.name));
      await postForm(`/api/cases/${c.id}/updates`, form);
      setText('');
      setPhotos([]);
      setResolve(false);
      void invalidate(queryClient, 'assignments');
      void invalidate(queryClient, 'case', c.id);
      void toast({ message: resolve ? t('doer.sentResolved', { count: residents }) : t('doer.sent', { count: residents }), duration: 3000, position: 'top', color: 'dark' });
    } catch (e) {
      void toast({ message: (e as Error).message, duration: 3000, color: 'danger' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <article className="card" style={{ gap: 12, padding: 16 }}>
      <div className="card__head">
        <span className="tag tag--ok tag--sm">{c.statusLabel}</span>
        <span className="small muted">{t('doer.residents', { count: residents })}</span>
        <span className="spacer" />
        <span className="small muted">{c.number}</span>
      </div>
      <h3 className="card__title">
        <AppLink to={`/case/${c.id}`} style={{ color: 'inherit' }}>
          {c.title?.text}
        </AppLink>
      </h3>
      {c.areaLabel && <span className="small muted">{c.areaLabel}</span>}

      {tasks.length > 0 && (
        <div className="stack-sm">
          <span className="eyebrow">{a.playbook ? t('doer.yourSteps', { title: a.playbook.title?.text }) : t('doer.steps')}</span>
          {mine.map((task) => (
            <label key={task.id} className="check" style={{ padding: '10px 12px' }}>
              <input type="checkbox" checked={isDone(task.id!, task.done)} onChange={(e) => tick(task.id!, e.target.checked)} />
              <span style={{ textDecoration: isDone(task.id!, task.done) ? 'line-through' : undefined }}>{task.title?.text}</span>
            </label>
          ))}
          {mine.length === 0 && <span className="small muted">{t('doer.noOwnSteps')}</span>}
          {others.length > 0 && (
            <>
              <button type="button" className="btn btn--ghost btn--sm" style={{ alignSelf: 'flex-start' }} onClick={() => setShowOthers(!showOthers)}>
                {showOthers ? t('doer.hideOthers') : t('doer.otherSteps', { count: others.length })}
              </button>
              {showOthers &&
                others.map((task) => (
                  <div key={task.id} className="row small" style={{ paddingLeft: 4 }}>
                    <IonIcon icon={task.done ? checkmarkCircle : ellipseOutline} style={{ color: task.done ? 'var(--brand)' : 'var(--line-3)' }} />
                    <span>{task.title?.text}</span>
                    <span className="muted">· {task.owner ?? t('doer.anyone')}</span>
                  </div>
                ))}
            </>
          )}
        </div>
      )}

      <label className="field">
        <span className="field__label">{t('doer.updateFor', { count: residents })}</span>
        <textarea className="textarea" style={{ minHeight: 96 }} value={text} onChange={(e) => setText(e.target.value)} placeholder={t('doer.updatePlaceholder')} />
      </label>
      {photos.length > 0 && (
        <div className="thumbs">
          {photos.map((p) => (
            <div className="thumb" key={p.name + p.size}>
              <img src={URL.createObjectURL(p)} alt="" />
              <button type="button" aria-label={t('report.removePhoto')} onClick={() => setPhotos((list) => list.filter((x) => x !== p))}>
                <IonIcon icon={closeOutline} />
              </button>
            </div>
          ))}
        </div>
      )}
      <label className="switch-row">
        <input type="checkbox" checked={resolve} onChange={(e) => setResolve(e.target.checked)} style={{ width: 22, height: 22, accentColor: 'var(--brand)' }} />
        <span>
          <strong>{t('doer.resolve')}</strong>
          <span className="small muted" style={{ display: 'block' }}>{t('doer.resolveHint')}</span>
        </span>
      </label>
      <div className="btn-row">
        <label className="btn btn--secondary btn--sm" style={{ flex: '0 0 auto' }}>
          <IonIcon icon={cameraOutline} aria-hidden="true" /> {t('doer.photo')}
          <input type="file" accept="image/*" capture="environment" multiple hidden onChange={(e) => setPhotos((list) => [...list, ...Array.from(e.target.files ?? [])].slice(0, 4))} />
        </label>
        <button type="button" className="btn btn--sm" disabled={busy || (!text.trim() && photos.length === 0 && !resolve)} onClick={send}>
          <IonIcon icon={sendOutline} aria-hidden="true" /> {resolve ? t('doer.sendResolve') : t('doer.send')}
        </button>
      </div>
    </article>
  );
}
