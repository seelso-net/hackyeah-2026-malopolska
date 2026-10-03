import { useEffect, useMemo, useRef, useState } from 'react';
import { IonContent, IonIcon, IonPage, useIonRouter } from '@ionic/react';
import {
  arrowBack,
  bulbOutline,
  cameraOutline,
  closeOutline,
  handLeftOutline,
  heartOutline,
  locationOutline,
  mic,
  stop,
  trashOutline,
} from 'ionicons/icons';
import { useTranslation } from 'react-i18next';
import { api, postForm, unwrap, type Accepted } from '../../api/client';
import { MapView } from '../../components/MapView';
import { seconds } from '../../lib/format';
import { useApi, useSession } from '../../session/Session';
import { centreOf } from './HomeMap';

type Kind = 'NEED' | 'IDEA' | 'OFFER';

interface Point {
  lat: number;
  lng: number;
}

/** The Web Speech API, where the browser has it (Chrome, Edge, Safari). Typing always works. */
interface Recognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start(): void;
  stop(): void;
  onresult: ((e: { resultIndex: number; results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }> }) => void) | null;
  onend: (() => void) | null;
  onerror: (() => void) | null;
}

function recognitionClass(): (new () => Recognition) | null {
  const w = window as unknown as { SpeechRecognition?: new () => Recognition; webkitSpeechRecognition?: new () => Recognition };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

const SPEECH_LANG: Record<string, string> = { pl: 'pl-PL', en: 'en-GB', uk: 'uk-UA' };

interface Photo {
  file: File;
  url: string;
}

export default function NewReport() {
  const { t } = useTranslation();
  const { community, lang } = useSession();
  const router = useIonRouter();
  const [kind, setKind] = useState<Kind>('NEED');
  const [text, setText] = useState('');
  const [recording, setRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [interim, setInterim] = useState('');
  const [audio, setAudio] = useState<{ blob: Blob; url: string; duration: number } | null>(null);
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [point, setPoint] = useState<Point | null>(null);
  const [address, setAddress] = useState('');
  const [picking, setPicking] = useState(false);
  const [anonymous, setAnonymous] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const recognition = useRef<Recognition | null>(null);
  const chunks = useRef<Blob[]>([]);
  const timer = useRef<number | null>(null);
  const elapsedRef = useRef(0);
  const textArea = useRef<HTMLTextAreaElement>(null);
  const canTranscribe = useMemo(() => recognitionClass() !== null, []);

  const map = useApi(['map', community], () => unwrap(api.GET('/api/map', { params: { query: { community } } })));
  const centre = useMemo(() => centreOf([...(map.data?.cases ?? []), ...(map.data?.initiatives ?? [])]), [map.data]);

  useEffect(() => {
    if (point || !centre) {
      return;
    }
    setPoint(centre);
    navigator.geolocation?.getCurrentPosition(
      (p) => {
        const gps = { lat: p.coords.latitude, lng: p.coords.longitude };
        const far = Math.abs(gps.lat - centre.lat) > 0.15 || Math.abs(gps.lng - centre.lng) > 0.25;
        if (!far) {
          setPoint(gps);
        }
      },
      () => {},
      { maximumAge: 120_000, timeout: 6_000 },
    );
  }, [centre, point]);

  useEffect(
    () => () => {
      recorder.current?.stream.getTracks().forEach((track) => track.stop());
      recognition.current?.stop();
      if (timer.current) {
        window.clearInterval(timer.current);
      }
    },
    [],
  );

  const startRecording = async () => {
    setError(null);
    setInterim('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mr = new MediaRecorder(stream);
      chunks.current = [];
      mr.ondataavailable = (e) => e.data.size > 0 && chunks.current.push(e.data);
      mr.onstop = () => {
        stream.getTracks().forEach((track) => track.stop());
        const blob = new Blob(chunks.current, { type: mr.mimeType || 'audio/webm' });
        setAudio((old) => {
          if (old) {
            URL.revokeObjectURL(old.url);
          }
          return { blob, url: URL.createObjectURL(blob), duration: elapsedRef.current };
        });
      };
      mr.start();
      recorder.current = mr;
    } catch {
      setError(t('report.micBlocked'));
    }
    const Rec = recognitionClass();
    if (Rec) {
      const rec = new Rec();
      rec.lang = SPEECH_LANG[lang] ?? 'pl-PL';
      rec.continuous = true;
      rec.interimResults = true;
      rec.onresult = (e) => {
        let finalText = '';
        let partial = '';
        for (let i = e.resultIndex; i < e.results.length; i++) {
          const r = e.results[i];
          if (r.isFinal) {
            finalText += r[0].transcript;
          } else {
            partial += r[0].transcript;
          }
        }
        if (finalText) {
          setText((old) => (old ? `${old.trim()} ${finalText.trim()}` : finalText.trim()));
        }
        setInterim(partial);
      };
      rec.onerror = () => setInterim('');
      rec.onend = () => setInterim('');
      try {
        rec.start();
        recognition.current = rec;
      } catch {
        recognition.current = null;
      }
    }
    setElapsed(0);
    elapsedRef.current = 0;
    timer.current = window.setInterval(() => {
      elapsedRef.current += 1;
      setElapsed(elapsedRef.current);
    }, 1000);
    setRecording(true);
  };

  const stopRecording = () => {
    recorder.current?.stop();
    recorder.current = null;
    recognition.current?.stop();
    recognition.current = null;
    if (timer.current) {
      window.clearInterval(timer.current);
      timer.current = null;
    }
    setRecording(false);
  };

  const addPhotos = (files: FileList | null) => {
    if (!files) {
      return;
    }
    const added = Array.from(files)
      .filter((f) => f.type.startsWith('image/'))
      .map((file) => ({ file, url: URL.createObjectURL(file) }));
    setPhotos((list) => [...list, ...added].slice(0, 4));
  };

  const send = async () => {
    if (text.trim().length < 3) {
      setError(t('report.needText'));
      textArea.current?.focus();
      return;
    }
    setSending(true);
    setError(null);
    try {
      const form = new FormData();
      form.set('community', community);
      form.set('kind', kind);
      form.set('text', text.trim());
      if (point) {
        form.set('lat', String(point.lat));
        form.set('lng', String(point.lng));
      }
      if (address.trim()) {
        form.set('address', address.trim());
      }
      form.set('anonymous', String(anonymous));
      if (audio) {
        const ext = audio.blob.type.includes('mp4') ? 'm4a' : audio.blob.type.includes('ogg') ? 'ogg' : 'webm';
        form.set('audio', audio.blob, `voice-note.${ext}`);
      }
      photos.forEach((p) => form.append('photo', p.file, p.file.name));
      const accepted = await postForm<Accepted>('/api/reports', form);
      router.push(`/report/${accepted.reportId}`, 'forward', 'replace');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSending(false);
    }
  };

  const kinds: { kind: Kind; icon: string; label: string }[] = [
    { kind: 'NEED', icon: handLeftOutline, label: t('report.kindNeed') },
    { kind: 'IDEA', icon: bulbOutline, label: t('report.kindIdea') },
    { kind: 'OFFER', icon: heartOutline, label: t('report.kindOffer') },
  ];

  return (
    <IonPage>
      <IonContent className="page">
        <div className="column">
          <header className="mobile-header">
            <button type="button" className="icon-btn" aria-label={t('common.back')} onClick={() => (router.canGoBack() ? router.goBack() : router.push('/home', 'back'))}>
              <IonIcon icon={arrowBack} />
            </button>
            <span className="mobile-header__title">{t('report.title')}</span>
            <span className="spacer" />
            <span className="steps-label">{t('report.step', { n: 1 })}</span>
          </header>

          <div className="pad stack-lg" style={{ paddingTop: 4 }}>
            <h1 className="h2">{t('report.question')}</h1>

            <div className="kind-picker" role="group" aria-label={t('report.kindLabel')}>
              {kinds.map((k) => (
                <button type="button" key={k.kind} aria-pressed={kind === k.kind} onClick={() => setKind(k.kind)}>
                  <IonIcon icon={k.icon} aria-hidden="true" />
                  {k.label}
                </button>
              ))}
            </div>

            <section className="recorder" aria-label={t('report.voice')}>
              <button
                type="button"
                className={`recorder__button${recording ? ' recorder__button--on' : ''}`}
                onClick={recording ? stopRecording : startRecording}
                aria-label={recording ? t('report.stop') : t('report.record')}
              >
                <IonIcon icon={recording ? stop : mic} />
              </button>
              {recording ? (
                <span className="recorder__status">
                  <span className="rec-dot" /> {t('report.recording')} · {seconds(elapsed)}
                </span>
              ) : (
                <span className="small muted" style={{ textAlign: 'center' }}>
                  {canTranscribe ? t('report.tapToTalk') : t('report.tapToRecord')}
                </span>
              )}
              {(recording || interim) && canTranscribe && (
                <div className="transcript" aria-live="polite">
                  <span className="eyebrow" style={{ display: 'block', marginBottom: 4 }}>{t('report.liveTranscript')}</span>
                  {text} <em className="muted">{interim}</em>
                </div>
              )}
              {audio && !recording && (
                <div className="row" style={{ width: '100%' }}>
                  <audio controls src={audio.url} style={{ flex: 1, height: 40 }} />
                  <button type="button" className="icon-btn" aria-label={t('report.deleteVoice')} onClick={() => setAudio(null)}>
                    <IonIcon icon={trashOutline} />
                  </button>
                </div>
              )}
            </section>

            <label className="field">
              <span className="field__label">{canTranscribe ? t('report.textLabelVoice') : t('report.textLabel')}</span>
              <textarea
                ref={textArea}
                className="textarea"
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder={t(`report.placeholder${kind}`)}
              />
            </label>

            <div className="stack-sm">
              <div className="thumbs">
                {photos.map((p, i) => (
                  <div className="thumb" key={p.url}>
                    <img src={p.url} alt={t('report.photoN', { n: i + 1 })} />
                    <button type="button" aria-label={t('report.removePhoto')} onClick={() => setPhotos((list) => list.filter((x) => x !== p))}>
                      <IonIcon icon={closeOutline} />
                    </button>
                  </div>
                ))}
              </div>
              <label className="btn btn--secondary btn--sm" style={{ alignSelf: 'flex-start' }}>
                <IonIcon icon={cameraOutline} aria-hidden="true" /> {t('report.addPhoto')}
                <input type="file" accept="image/*" capture="environment" multiple hidden onChange={(e) => addPhotos(e.target.files)} />
              </label>
            </div>

            <div className="stack-sm">
              <div className="place">
                <IonIcon icon={locationOutline} aria-hidden="true" />
                <input
                  className="input"
                  style={{ border: 0, minHeight: 36, padding: 0 }}
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder={t('report.addressPlaceholder')}
                  aria-label={t('report.address')}
                />
                <button type="button" className="btn btn--ghost btn--sm" onClick={() => setPicking(!picking)}>
                  {picking ? t('common.done') : t('report.change')}
                </button>
              </div>
              {picking && point && (
                <MapView
                  className="map map--panel"
                  label={t('report.pickOnMap')}
                  pins={[{ id: 'here', lat: point.lat, lng: point.lng, kind: 'need', label: '' }]}
                  follow={[point.lat, point.lng]}
                  zoom={16}
                  onPick={(lat, lng) => setPoint({ lat, lng })}
                />
              )}
              {picking && <span className="tiny muted">{t('report.pickHint')}</span>}
            </div>

            <label className="switch-row">
              <input type="checkbox" checked={anonymous} onChange={(e) => setAnonymous(e.target.checked)} style={{ width: 22, height: 22, accentColor: 'var(--brand)' }} />
              <span>
                <strong>{t('report.anonymous')}</strong>
                <span className="small muted" style={{ display: 'block' }}>{anonymous ? t('report.anonymousOn') : t('report.anonymousOff')}</span>
              </span>
            </label>

            {error && <div className="notice notice--danger">{error}</div>}
          </div>
        </div>
      </IonContent>
      <div className="footer-bar">
        <div className="footer-bar__inner stack-sm">
          <button type="button" className="btn btn--lg btn--block" onClick={send} disabled={sending || recording}>
            {sending ? t('report.sending') : t('report.continue')}
          </button>
        </div>
      </div>
    </IonPage>
  );
}
