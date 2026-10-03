import { IonSpinner } from '@ionic/react';
import { useTranslation } from 'react-i18next';

export function Loading({ label }: { label?: string }) {
  const { t } = useTranslation();
  return (
    <div className="center-fill" role="status">
      <IonSpinner name="crescent" color="primary" />
      <span className="muted">{label ?? t('common.loading')}</span>
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation();
  const message = error instanceof Error ? error.message : String(error);
  return (
    <div className="center-fill" role="alert">
      <span className="h3">{t('common.errorTitle')}</span>
      <span className="muted">{message}</span>
      {onRetry && (
        <button type="button" className="btn btn--secondary btn--sm" onClick={onRetry}>
          {t('common.retry')}
        </button>
      )}
    </div>
  );
}
