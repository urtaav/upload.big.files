import type { FlowSettings } from '../hooks/useUploadFlow';
import type { UploaderState } from '../lib/uploader';

const POSITIONS = [1, 2, 3, 4, 5, 6, 7, 8];

interface ControlsProps {
  settings: FlowSettings;
  state: UploaderState;
  onSettings: (next: FlowSettings) => void;
  onResumeFromServer: () => void;
}

/**
 * The bench controls. They exist to break the machine on purpose, so nothing
 * here guards the operator against an outcome he asked for.
 */
export function Controls({ settings, state, onSettings, onResumeFromServer }: ControlsProps) {
  const busy = state.status === 'uploading' || state.status === 'completing';
  const resumable =
    Boolean(state.uploadId) && ['failed', 'paused', 'uploading', 'done'].includes(state.status);

  return (
    <div className="controls">
      <div>
        <div className="control__head">
          <span>Canales simultáneos</span>
          <b>{String(settings.concurrency).padStart(2, '0')}</b>
        </div>
        <div className="detent">
          {POSITIONS.map((position) => (
            <button
              key={position}
              data-on={settings.concurrency === position}
              disabled={busy}
              onClick={() => onSettings({ ...settings, concurrency: position })}
            >
              {position}
            </button>
          ))}
        </div>
        <p className="control__note">
          Sube con la posición 1 y repite en la 8: el archivo es el mismo, el reloj no. Se
          aplica al empezar una transmisión.
        </p>
      </div>

      <div>
        <div className="control__head">
          <span>Inyectar fallo en canal</span>
          <b>{settings.failPart ?? '--'}</b>
        </div>
        <input
          className="field"
          type="number"
          min={1}
          placeholder="nº de canal · vacío = ninguno"
          value={settings.failPart ?? ''}
          disabled={busy}
          onChange={(event) =>
            onSettings({
              ...settings,
              failPart: event.target.value ? Number(event.target.value) : null,
            })
          }
        />
        <p className="control__note">
          El primer intento de ese canal falla a propósito. Mira el reintento con backoff en
          el log: el estado del servidor no se mueve hasta que la parte entra de verdad. Si
          agota los intentos, <b>Transmitir</b> continúa la misma sesión, no abre otra.
        </p>
      </div>

      <div>
        <button className="key key--dark" disabled={!resumable} onClick={onResumeFromServer}>
          Purgar memoria local
        </button>
        <p className="control__note">
          Tira todo lo que sabe el navegador y reconstruye la transmisión con un solo{' '}
          <code>GET /v1/uploads/&#123;id&#125;</code>. Es lo que haría una recarga, salvo el
          archivo, que ningún navegador puede persistir.
        </p>
      </div>
    </div>
  );
}
