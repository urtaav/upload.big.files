import { useEffect, useState, type ReactNode } from 'react';
import { Panel } from './rack/Panel';
import { Indicator } from './rack/Lamp';
import { Segments, padDigits } from './rack/Segments';
import { formatBytes, formatRate, shortId } from '../lib/format';
import type { UploaderState } from '../lib/uploader';
import type { UploadResponse } from '../lib/types';

interface TransportProps {
  file: File;
  state: UploaderState;
  serverSession: UploadResponse | null;
  children: ReactNode;
}

/** Ticks while something is moving, so elapsed time and rate stay alive. */
function useRunningClock(active: boolean) {
  const [, tick] = useState(0);
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => tick((value) => value + 1), 250);
    return () => clearInterval(timer);
  }, [active]);
}

function timecode(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const mm = padDigits(Math.floor(total / 60), 2).replace(/ /g, '0');
  const ss = padDigits(total % 60, 2).replace(/ /g, '0');
  return `${mm}:${ss}`;
}

export function Transport({ file, state, serverSession, children }: TransportProps) {
  const running = state.status === 'uploading' || state.status === 'completing';
  useRunningClock(running);

  const total = state.totalBytes || file.size;
  const ratio = total > 0 ? Math.min(1, state.bytesSent / total) : 0;
  const elapsed = state.startedAt ? (state.finishedAt ?? Date.now()) - state.startedAt : 0;
  const rate = elapsed > 0 ? (state.bytesSent / elapsed) * 1000 : 0;
  const done = state.status === 'done';
  const inFlight = state.parts.filter((part) => part.state === 'uploading').length;
  const stored = state.parts.filter((part) => part.state === 'uploaded').length;
  const faulted = state.parts.some((part) => part.state === 'failed');

  return (
    <Panel
      alu
      designation="Transporte"
      stencil={state.uploadId ? `id ${shortId(state.uploadId)}` : 'sin sesión'}
      controls={
        <span className="spec" style={{ color: 'var(--engraved-dim)' }}>
          {serverSession ? `API · ${serverSession.status}` : 'API · —'}
        </span>
      }
      legend="El porcentaje mide bytes que ya salieron del navegador, no partes terminadas. La cinta nunca se carga entera en memoria: cada canal es un Blob recortado al vuelo."
    >
      <div className="transport">
        <div className="transport__slate">
          <div className="transport__reel">{file.name}</div>
          <div className="transport__stamp">
            {file.type || 'application/octet-stream'} · {formatBytes(file.size)} ·{' '}
            {state.parts.length || serverSession?.totalParts || '—'} canales ·{' '}
            {formatBytes(serverSession?.partSize ?? state.parts[0]?.size ?? 0, 0)} c/u
          </div>
          <div className="transport__stamp">
            {serverSession?.objectKey ?? state.session?.objectKey ?? 'sin clave asignada'}
          </div>

          <div className="scale">
            <div
              className={`scale__fill${done ? ' scale__fill--done' : ''}`}
              style={{ width: `${ratio * 100}%` }}
            />
            <div className="scale__ticks" />
          </div>

          <div className="transport__tallies">
            <Indicator label="ON AIR" colour="tally" on={inFlight > 0} />
            <Indicator label="FAULT" colour="fault" on={faulted || state.status === 'failed'} blink />
            <Indicator label="READY" colour="ready" on={done} />
            <Indicator
              label="PLAYBACK"
              colour="playback"
              on={state.parts.some((part) => part.restored)}
            />
          </div>

          <div className="transport__keys">{children}</div>
        </div>

        <div className="transport__meters">
          <div className="gauge">
            <span className="gauge__label">Transmitido</span>
            <span className="readout">
              <Segments value={padDigits(Math.round(ratio * 100), 3)} height={44} />
              <span className="readout__unit">%</span>
            </span>
          </div>
          <div className="gauge">
            <span className="gauge__label">Tiempo</span>
            <span className="readout">
              <Segments value={timecode(elapsed)} height={30} />
            </span>
          </div>
          <div className="gauge">
            <span className="gauge__label">Canales ok</span>
            <span className="readout">
              <Segments value={padDigits(stored, 3)} height={30} />
              <span className="readout__unit">
                de {state.parts.length || serverSession?.totalParts || 0}
              </span>
            </span>
          </div>
          <div className="gauge">
            <span className="gauge__label">Caudal</span>
            <span className="readout">
              <span
                className="readout__unit"
                style={{ fontSize: 12, paddingBottom: 0, color: '#ff8a6a' }}
              >
                {running || done ? formatRate(rate) : '—'}
              </span>
            </span>
          </div>
        </div>
      </div>
    </Panel>
  );
}
