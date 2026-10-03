import { IonIcon } from '@ionic/react';
import { checkmark } from 'ionicons/icons';

export interface Step {
  label: string;
}

/** Progress through the case lifecycle; `current` is the step waiting to happen. */
export function Stepper({ steps, done }: { steps: Step[]; done: number }) {
  const n = steps.length;
  const half = 100 / n / 2;
  const doneEdge = Math.min(done, n - 1);
  return (
    <div className="stepper" aria-label="Progress">
      <span className="stepper__track" style={{ left: `${half}%`, right: `${half}%` }} />
      <span
        className="stepper__fill"
        style={{ left: `${half}%`, width: `${doneEdge <= 0 ? 0 : (doneEdge / (n - 1)) * (100 - 2 * half)}%` }}
      />
      <ol style={{ gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))` }}>
        {steps.map((s, i) => {
          const state = i < done ? 'done' : i === done ? 'current' : '';
          return (
            <li key={s.label} className={state} aria-current={state === 'current' ? 'step' : undefined}>
              <span className="stepper__dot">{state === 'done' && <IonIcon icon={checkmark} />}</span>
              {s.label}
            </li>
          );
        })}
      </ol>
    </div>
  );
}
