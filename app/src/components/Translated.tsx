import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TextDto } from '../api/client';

/** People's words in the reader's language; machine translations say so and can show the original. */
export function Translated({ value, as: Tag = 'span', className }: { value?: TextDto | null; as?: 'span' | 'p' | 'div'; className?: string }) {
  const { t } = useTranslation();
  const [original, setOriginal] = useState(false);
  if (!value?.text) {
    return null;
  }
  const showOriginal = original && value.machineTranslated && value.original;
  return (
    <>
      <Tag className={className}>{showOriginal ? value.original : value.text}</Tag>
      {value.machineTranslated && value.original && (
        <span className="translated">
          {showOriginal ? t('text.original') : t('text.translated')} ·{' '}
          <button type="button" onClick={() => setOriginal(!original)}>
            {showOriginal ? t('text.showTranslation') : t('text.showOriginal')}
          </button>
        </span>
      )}
    </>
  );
}

export function plain(value?: TextDto | null) {
  return value?.text ?? '';
}
