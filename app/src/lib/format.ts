/** Small formatting helpers that follow the reader's language. */

export function timeAgo(iso: string | undefined | null, lang: string): string {
  if (!iso) {
    return '';
  }
  const seconds = Math.round((new Date(iso).getTime() - Date.now()) / 1000);
  const rtf = new Intl.RelativeTimeFormat(lang, { numeric: 'auto' });
  const abs = Math.abs(seconds);
  if (abs < 60) {
    return rtf.format(Math.round(seconds), 'second');
  }
  if (abs < 3600) {
    return rtf.format(Math.round(seconds / 60), 'minute');
  }
  if (abs < 86400) {
    return rtf.format(Math.round(seconds / 3600), 'hour');
  }
  if (abs < 86400 * 30) {
    return rtf.format(Math.round(seconds / 86400), 'day');
  }
  return rtf.format(Math.round(seconds / (86400 * 30)), 'month');
}

export function dateTime(iso: string | undefined | null, lang: string): string {
  if (!iso) {
    return '';
  }
  return new Intl.DateTimeFormat(lang, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(
    new Date(iso),
  );
}

export function distance(meters: number | undefined | null, lang: string): string {
  if (meters === undefined || meters === null || Number.isNaN(meters)) {
    return '';
  }
  if (meters < 1000) {
    return `${Math.max(10, Math.round(meters / 10) * 10)} m`;
  }
  return `${new Intl.NumberFormat(lang, { maximumFractionDigits: 1 }).format(meters / 1000)} km`;
}

/** Metres between two points (equirectangular; fine within a city). */
export function metersBetween(a?: { lat?: number; lng?: number } | null, b?: { lat?: number; lng?: number } | null) {
  if (!a || !b || a.lat == null || a.lng == null || b.lat == null || b.lng == null) {
    return undefined;
  }
  const x = ((b.lng - a.lng) * Math.PI) / 180 * Math.cos((((a.lat + b.lat) / 2) * Math.PI) / 180);
  const y = ((b.lat - a.lat) * Math.PI) / 180;
  return Math.sqrt(x * x + y * y) * 6_371_000;
}

export function initials(name?: string | null): string {
  if (!name) {
    return '?';
  }
  const parts = name.replace(/[.,]/g, ' ').split(/\s+/).filter(Boolean);
  const letters = parts.length === 1 ? parts[0].slice(0, 2) : parts[0][0] + parts[parts.length - 1][0];
  return letters.toUpperCase();
}

export function seconds(value: number): string {
  const m = Math.floor(value / 60);
  const s = Math.floor(value % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}
