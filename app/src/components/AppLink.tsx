import type { AnchorHTMLAttributes, MouseEvent } from 'react';
import { useIonRouter, type RouteAction, type RouterDirection } from '@ionic/react';

type Props = Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href'> & {
  to: string;
  direction?: RouterDirection;
  action?: RouteAction;
};

/**
 * An in-app link. It keeps a real href (open in a new tab, copy the link, screen readers),
 * but a plain click goes through Ionic's router so the page transition and the back stack stay right.
 */
export function AppLink({ to, direction = 'forward', action = 'push', onClick, ...rest }: Props) {
  const router = useIonRouter();
  const follow = (e: MouseEvent<HTMLAnchorElement>) => {
    onClick?.(e);
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) {
      return;
    }
    e.preventDefault();
    router.push(to, direction, action);
  };
  return <a {...rest} href={to} onClick={follow} />;
}
