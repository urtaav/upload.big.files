import { Lamp } from './rack/Lamp';
import type { UploadStatus } from '../lib/types';

/** The happy path of `UploadStatus`, in the order the enum permits. */
const STAGES: Array<{ status: UploadStatus; hint: string; wired: boolean }> = [
  { status: 'CREATED', hint: 'Sesión en Postgres. Todavía no hay bytes.', wired: true },
  { status: 'UPLOADING', hint: 'Canales transmitiendo directo al almacenamiento.', wired: true },
  { status: 'COMPLETING', hint: 'CAS tomado. S3 ensambla el objeto.', wired: true },
  { status: 'COMPLETED', hint: 'El objeto existe. Fin de lo síncrono.', wired: true },
  { status: 'PROCESSING', hint: 'Sin consumidor: nadie acciona este relé.', wired: false },
  { status: 'READY', hint: 'Inalcanzable mientras el cable siga cortado.', wired: false },
];

export function SignalPath({ status }: { status?: UploadStatus }) {
  const index = status ? STAGES.findIndex((stage) => stage.status === status) : -1;

  return (
    <div className="path">
      {STAGES.map((stage, position) => {
        const past = index >= 0 && position < index;
        const now = position === index;
        const unwired = !stage.wired && !past && !now;

        return (
          <div
            className={[
              'path__stage',
              past ? 'path__stage--past' : '',
              now ? 'path__stage--now' : '',
              unwired ? 'path__stage--unwired' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            key={stage.status}
          >
            <div className="path__rail">
              <Lamp colour={now ? 'tally' : 'ready'} on={past || now} />
              {position < STAGES.length - 1 ? <span className="path__wire" /> : null}
            </div>
            <div>
              <div className="path__name">{stage.status}</div>
              <div className="path__hint">{stage.hint}</div>
              {stage.status === 'PROCESSING' && !past && !now ? (
                <div className="path__cut">▸ cable cortado desde aquí</div>
              ) : null}
            </div>
          </div>
        );
      })}
    </div>
  );
}
