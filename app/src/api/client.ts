import createClient, { type Middleware } from 'openapi-fetch';
import type { components, paths } from './schema';

/** Empty in the browser (same origin as the backend); set VITE_API_URL for native builds. */
export const API_BASE: string = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');

/** Who is calling and in which language; set by the session, read by every request. */
const context = { login: null as string | null, lang: 'en' };

export function setApiContext(login: string | null, lang: string) {
  context.login = login;
  context.lang = lang;
}

export function currentLogin() {
  return context.login;
}

// Headers only: rebuilding the Request to change its URL would turn JSON bodies into streams that fetch rejects.
const demoAuth: Middleware = {
  onRequest({ request }) {
    request.headers.set('X-Lang', context.lang);
    if (context.login) {
      request.headers.set('X-Demo-User', context.login);
    }
    return request;
  },
};

export const api = createClient<paths>({ baseUrl: API_BASE || window.location.origin });
api.use(demoAuth);

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

type Result<T> = { data?: T; error?: unknown; response: Response };

/** Returns the data or throws an ApiError carrying the backend's {"error": "..."} message. */
export async function unwrap<T>(call: Promise<Result<T>>): Promise<T> {
  const { data, error, response } = await call;
  if (error !== undefined || !response.ok) {
    const message =
      error && typeof error === 'object' && 'error' in error ? String((error as { error: unknown }).error) : response.statusText;
    throw new ApiError(message || `HTTP ${response.status}`, response.status);
  }
  return data as T;
}

/** Multipart uploads (reports with photos and voice, doer updates with photos). */
export async function postForm<T>(path: string, form: FormData): Promise<T> {
  const url = new URL(`${API_BASE}${path}`, window.location.origin);
  url.searchParams.set('lang', context.lang);
  const response = await fetch(url, {
    method: 'POST',
    body: form,
    headers: context.login ? { 'X-Demo-User': context.login } : {},
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(body?.error ?? response.statusText, response.status);
  }
  return body as T;
}

/** Photos and voice notes are loaded by <img>/<audio>, which cannot send headers: pass the login as ?as=. */
export function mediaUrl(url?: string | null) {
  if (!url) {
    return undefined;
  }
  const full = new URL(`${API_BASE}${url}`, window.location.origin);
  if (context.login) {
    full.searchParams.set('as', context.login);
  }
  return full.toString();
}

export function streamUrl(login: string) {
  const url = new URL(`${API_BASE}/api/stream`, window.location.origin);
  url.searchParams.set('as', login);
  return url.toString();
}

type S = components['schemas'];
export type TextDto = S['TextDto'];
export type CaseCard = S['CaseCard'];
export type CaseDetail = S['CaseDetail'];
export type TimelineItem = S['TimelineItem'];
export type Helper = S['Helper'];
export type TaskView = S['TaskView'];
export type MediaView = S['MediaView'];
export type CommunityView = S['CommunityView'];
export type CommunityItem = S['CommunityItem'];
export type MapView = S['MapView'];
export type MapInitiative = S['MapInitiative'];
export type MeView = S['MeView'];
export type MyCase = S['MyCase'];
export type ReportView = S['ReportView'];
export type SubmitResult = S['SubmitResult'];
export type QueueView = S['QueueView'];
export type QueueItem = S['QueueItem'];
export type ModerationDetail = S['ModerationDetail'];
export type SuggestionView = S['SuggestionView'];
export type CandidateView = S['CandidateView'];
export type ReportItem = S['ReportItem'];
export type AssignmentView = S['AssignmentView'];
export type PlaybookView = S['PlaybookView'];
export type PlaybookItem = S['PlaybookItem'];
export type VersionView = S['VersionView'];
export type StepView = S['StepView'];
export type CommunityConfig = S['CommunityConfig'];
export type StreamEvent = S['StreamEvent'];
export type SubmitReport = S['SubmitReport'];
/** POST /api/reports answers 202 with this body. */
export interface Accepted {
  reportId: string;
  status: string;
  next: string;
}
