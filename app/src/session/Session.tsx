import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery, useQueryClient, type QueryClient, type UseQueryOptions } from '@tanstack/react-query';
import i18n, { isLang, type Lang } from '../i18n';
import { api, setApiContext, unwrap, type MeView } from '../api/client';

const LOGIN_KEY = 'needs.login';

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, value);
    }
  } catch {
    // Private mode or blocked storage: the session simply is not remembered.
  }
}

function browserLang(): Lang {
  const code = (navigator.language || 'en').slice(0, 2);
  return isLang(code) ? code : 'en';
}

export interface Session {
  login: string | null;
  me?: MeView;
  meLoading: boolean;
  lang: Lang;
  community: string;
  /** Roles the user holds in the selected community: RESIDENT, MODERATOR, DOER, ADMIN. */
  roles: string[];
  isModerator: boolean;
  isAdmin: boolean;
  isDoer: boolean;
  isResident: boolean;
  /** The organisation (or volunteer profile) the user acts for in the selected community. */
  doerActor?: { id: string; name: string };
  homePath: string;
  signIn(login: string): void;
  signOut(): void;
  setLang(lang: Lang): void;
  setCommunity(slug: string): void;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [login, setLogin] = useState<string | null>(() => read(LOGIN_KEY));
  const [langChoice, setLangChoice] = useState<string | null>(() => (login ? read(`needs.lang.${login}`) : null));
  const [communityChoice, setCommunityChoice] = useState<string | null>(() =>
    login ? read(`needs.community.${login}`) : null,
  );

  // Keep the API client in step during render, so the first queries already carry the login and language.
  const provisionalLang: Lang = isLang(langChoice) ? langChoice : browserLang();
  setApiContext(login, provisionalLang);

  const meQuery = useQuery({
    queryKey: ['me', login],
    queryFn: () => unwrap(api.GET('/api/me')),
    enabled: !!login,
    retry: false,
    staleTime: 60_000,
  });
  const me = meQuery.data;

  const lang: Lang = isLang(langChoice) ? langChoice : isLang(me?.locale) ? (me!.locale as Lang) : browserLang();
  setApiContext(login, lang);
  useEffect(() => {
    if (i18n.language !== lang) {
      void i18n.changeLanguage(lang);
    }
    document.documentElement.lang = lang;
  }, [lang]);

  const memberships = me?.memberships ?? [];
  const community = communityChoice ?? memberships.find((m) => m.role !== 'RESIDENT')?.community ?? memberships[0]?.community ?? 'riverside';
  const mine = memberships.filter((m) => m.community === community);
  const roles = [...new Set(mine.map((m) => m.role ?? ''))].filter(Boolean);
  const doer = mine.find((m) => m.role === 'DOER' && m.actorId);
  const isAdmin = roles.includes('ADMIN');
  const isModerator = isAdmin || roles.includes('MODERATOR');
  const isDoer = !!doer;
  const isResident = roles.includes('RESIDENT') || roles.length === 0;

  const signIn = useCallback(
    (next: string) => {
      queryClient.clear();
      write(LOGIN_KEY, next);
      setLogin(next);
      setLangChoice(read(`needs.lang.${next}`));
      setCommunityChoice(read(`needs.community.${next}`));
    },
    [queryClient],
  );

  const signOut = useCallback(() => {
    queryClient.clear();
    write(LOGIN_KEY, null);
    setLogin(null);
  }, [queryClient]);

  const setLang = useCallback(
    (next: Lang) => {
      if (login) {
        write(`needs.lang.${login}`, next);
      }
      setLangChoice(next);
    },
    [login],
  );

  const setCommunity = useCallback(
    (slug: string) => {
      if (login) {
        write(`needs.community.${login}`, slug);
      }
      setCommunityChoice(slug);
    },
    [login],
  );

  const value = useMemo<Session>(
    () => ({
      login,
      me,
      meLoading: meQuery.isLoading,
      lang,
      community,
      roles,
      isModerator,
      isAdmin,
      isDoer,
      isResident,
      doerActor: doer ? { id: doer.actorId!, name: doer.actorName ?? '' } : undefined,
      homePath: !login ? '/signin' : isModerator ? '/moderation' : isDoer && !roles.includes('RESIDENT') ? '/doer' : '/home',
      signIn,
      signOut,
      setLang,
      setCommunity,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [login, me, meQuery.isLoading, lang, community, roles.join(','), isModerator, isDoer, doer?.actorId, signIn, signOut, setLang, setCommunity],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error('useSession outside SessionProvider');
  }
  return session;
}

/** A query scoped to the signed-in user and language, so switching either never shows stale text. */
export function useApi<T>(
  key: readonly unknown[],
  fn: () => Promise<T>,
  options?: Omit<UseQueryOptions<T, Error, T, readonly unknown[]>, 'queryKey' | 'queryFn'>,
) {
  const { login, lang } = useSession();
  return useQuery<T, Error, T, readonly unknown[]>({
    ...options,
    queryKey: ['api', login, lang, ...key],
    queryFn: fn,
    enabled: !!login && (options?.enabled ?? true),
  });
}

/** Refetches every query for a resource (and optionally one id), whoever and whatever language it is for. */
export function invalidate(queryClient: QueryClient, resource: string, id?: string | null) {
  return queryClient.invalidateQueries({
    predicate: (q) => q.queryKey[0] === 'api' && q.queryKey[3] === resource && (id == null || q.queryKey[4] === id),
  });
}
