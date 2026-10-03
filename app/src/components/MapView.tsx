import { useEffect, useMemo, useRef } from 'react';
import L from 'leaflet';
import { MapContainer, Marker, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet';

export interface Pin {
  id: string;
  lat: number;
  lng: number;
  kind: 'need' | 'idea' | 'initiative' | 'closed' | 'me' | 'report';
  label?: string;
  title?: string;
  big?: boolean;
  onClick?: () => void;
}

const DEFAULT_CENTER: [number, number] = [52.2496, 21.041];

function icon(pin: Pin) {
  const size = pin.kind === 'me' ? 16 : pin.kind === 'report' ? 14 : pin.big ? 44 : 34;
  const cls = `pin pin--${pin.kind === 'need' ? 'need' : pin.kind}${pin.big ? ' pin--big' : ''}`;
  const html = `<span class="${cls}" style="width:${size}px;height:${size}px">${pin.label ?? ''}</span>`;
  return L.divIcon({ html, className: '', iconSize: [size, size], iconAnchor: [size / 2, size / 2] });
}

/** Fits the view to the pins once they first arrive, then leaves panning to the person. */
function FitOnce({ pins, padding }: { pins: Pin[]; padding: number }) {
  const map = useMap();
  const fitted = useRef(false);
  useEffect(() => {
    if (fitted.current || pins.length === 0) {
      return;
    }
    fitted.current = true;
    if (pins.length === 1) {
      map.setView([pins[0].lat, pins[0].lng], 16);
      return;
    }
    map.fitBounds(L.latLngBounds(pins.map((p) => [p.lat, p.lng] as [number, number])), { padding: [padding, padding], maxZoom: 16 });
  }, [map, pins, padding]);
  return null;
}

function Picker({ onPick }: { onPick?: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onPick?.(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

/** Re-centres when the parent moves the focus point (e.g. a picked location). */
function Follow({ center }: { center?: [number, number] }) {
  const map = useMap();
  useEffect(() => {
    if (center) {
      map.panTo(center);
    }
  }, [map, center?.[0], center?.[1]]); // eslint-disable-line react-hooks/exhaustive-deps
  return null;
}

export function MapView({
  pins,
  className = 'map',
  label,
  onPick,
  follow,
  fitPadding = 40,
  zoom = 15,
}: {
  pins: Pin[];
  className?: string;
  label: string;
  onPick?: (lat: number, lng: number) => void;
  follow?: [number, number];
  fitPadding?: number;
  zoom?: number;
}) {
  const center = useMemo<[number, number]>(() => (pins[0] ? [pins[0].lat, pins[0].lng] : DEFAULT_CENTER), [pins]);
  return (
    <div className={className} role="region" aria-label={label}>
      <MapContainer center={follow ?? center} zoom={zoom} style={{ height: '100%', width: '100%' }} scrollWheelZoom={false}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {!follow && <FitOnce pins={pins.filter((p) => p.kind !== 'me')} padding={fitPadding} />}
        <Follow center={follow} />
        <Picker onPick={onPick} />
        {pins.map((p) => (
          <Marker
            key={p.id}
            position={[p.lat, p.lng]}
            icon={icon(p)}
            title={p.title}
            eventHandlers={p.onClick ? { click: p.onClick } : undefined}
            keyboard={!!p.onClick}
          >
            {p.title && <Tooltip direction="top" offset={[0, -18]}>{p.title}</Tooltip>}
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}
