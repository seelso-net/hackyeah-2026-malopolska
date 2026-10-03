import { useTranslation } from 'react-i18next';
import { LANGUAGES, isLang } from '../i18n';
import { useSession } from '../session/Session';

export function LanguageSelect({ className, short, style }: { className?: string; short?: boolean; style?: React.CSSProperties }) {
  const { t } = useTranslation();
  const { lang, setLang } = useSession();
  return (
    <select className={className} style={style} value={lang} aria-label={t('profile.language')} onChange={(e) => isLang(e.target.value) && setLang(e.target.value)}>
      {LANGUAGES.map((l) => (
        <option key={l.code} value={l.code}>
          {short ? l.short : l.label}
        </option>
      ))}
    </select>
  );
}
