import { initials } from '../lib/format';

export function Avatar({ name, size, tone }: { name?: string | null; size?: 'sm' | 'lg'; tone?: 'amber' | 'grey' }) {
  const cls = ['avatar', size ? `avatar--${size}` : '', tone ? `avatar--${tone}` : ''].filter(Boolean).join(' ');
  return (
    <span className={cls} aria-hidden="true">
      {initials(name)}
    </span>
  );
}

export function AvatarStack({ names, extra }: { names: string[]; extra?: number }) {
  return (
    <span className="avatar-stack" aria-hidden="true">
      {names.map((n, i) => (
        <Avatar key={`${n}-${i}`} name={n} size="sm" tone={i % 2 ? 'amber' : undefined} />
      ))}
      {extra && extra > 0 ? <span className="avatar avatar--sm avatar--grey">+{extra}</span> : null}
    </span>
  );
}
