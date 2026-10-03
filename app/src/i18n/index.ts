import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './en';
import pl from './pl';
import uk from './uk';

export const LANGUAGES = [
  { code: 'pl', label: 'Polski', short: 'PL' },
  { code: 'en', label: 'English', short: 'EN' },
  { code: 'uk', label: 'Українська', short: 'UK' },
] as const;

export type Lang = (typeof LANGUAGES)[number]['code'];

export function isLang(value: unknown): value is Lang {
  return LANGUAGES.some((l) => l.code === value);
}

void i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, pl: { translation: pl }, uk: { translation: uk } },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  returnNull: false,
});

export default i18n;
