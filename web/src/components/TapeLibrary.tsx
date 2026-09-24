import type { LibraryFilter } from '../hooks/useUploadLibrary';
import type { UploadListResponse, UploadStatus, UploadSummary } from '../lib/types';
import { formatBytes } from '../lib/format';
import { Lamp, type LampColour } from './rack/Lamp';

const FILTERS: LibraryFilter[] = ['todos', 'listos', 'incompletos'];

const LAMP_BY_STATUS: Partial<Record<UploadStatus, LampColour>> = {
  COMPLETED: 'ready',
  READY: 'ready',
  PROCESSING: 'fault',
  UPLOADING: 'tally',
  CREATED: 'playback',
  FAILED: 'fault',
  CANCELLED: 'fault',
  EXPIRED: 'fault',
};

interface TapeLibraryProps {
  data: UploadListResponse | null;
  filter: LibraryFilter;
  page: number;
  loading: boolean;
  error: string | null;
  onPage: (page: number) => void;
  onDownload: (uploadId: string) => void;
  onResume: (summary: UploadSummary) => void;
  canResume: boolean;
}

function stamp(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function TapeLibrary({
  data,
  filter,
  page,
  loading,
  error,
  onPage,
  onDownload,
  onResume,
  canResume,
}: TapeLibraryProps) {
  const items = data?.items ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <>
      {error ? (
        <div className="fault-strip" style={{ marginBottom: 16 }}>
          <div>
            <div className="fault-strip__title">No se pudo leer el catálogo</div>
            <div className="fault-strip__detail">{error}</div>
          </div>
        </div>
      ) : null}

      {items.length === 0 && !loading ? (
        <p className="blank">
          Nada bajo este filtro. Lo que subas aparece aquí, venga de esta pestaña o de otra
          máquina: el listado lo sirve el API, no este navegador.
        </p>
      ) : null}

      <ul className="library">
        {items.map((item, index) => (
          <li className="reel" key={item.uploadId}>
            <span className="reel__no">
              {String(page * (data?.size ?? 8) + index + 1).padStart(3, '0')}
            </span>

            <div style={{ minWidth: 0 }}>
              <div className="reel__title" title={item.fileName}>
                {item.fileName}
              </div>
              <div className="reel__stamp">
                {formatBytes(item.size)} · {item.contentType || 'sin tipo'} ·{' '}
                {stamp(item.createdAt)}
                {item.resumable ? ` · ${item.uploadedParts}/${item.totalParts} canales` : ''}
              </div>
            </div>

            <span className="reel__state">
              <Lamp
                colour={LAMP_BY_STATUS[item.status] ?? 'playback'}
                on
                blink={item.status === 'PROCESSING'}
              />
              {item.status}
            </span>

            <div className="reel__keys">
              {item.downloadable ? (
                <button className="key key--sm" onClick={() => onDownload(item.uploadId)}>
                  Descargar
                </button>
              ) : null}
              {item.resumable ? (
                <button
                  className="key key--sm key--dark"
                  disabled={!canResume}
                  title={
                    canResume
                      ? 'Continúa esta sesión con la cinta montada'
                      : 'Monta primero el archivo original para poder reanudar'
                  }
                  onClick={() => onResume(item)}
                >
                  Reanudar
                </button>
              ) : null}
            </div>
          </li>
        ))}
      </ul>

      {totalPages > 1 ? (
        <div className="pager">
          <button className="key key--sm key--dark" disabled={page <= 0} onClick={() => onPage(page - 1)}>
            Anterior
          </button>
          <span className="pager__count">
            {page + 1} / {totalPages} · filtro {filter}
          </span>
          <button
            className="key key--sm key--dark"
            disabled={page + 1 >= totalPages}
            onClick={() => onPage(page + 1)}
          >
            Siguiente
          </button>
        </div>
      ) : null}
    </>
  );
}

export { FILTERS as LIBRARY_FILTERS };
