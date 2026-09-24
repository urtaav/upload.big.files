import { useEffect, useState } from 'react';
import type { FlowEvent } from '../lib/events';
import type { UploaderState } from '../lib/uploader';

/** How long a jack stays lit after the traffic that lit it. */
const HOT_MS = 1100;

/* Jack positions. The byte cord is routed along the floor of the bay on
   purpose: it has to be visible that it never reaches the API jack. */
const BROWSER = { x: 92, y: 74 };
const API = { x: 442, y: 74 };
const STORE = { x: 792, y: 238 };
const DB = { x: 792, y: 74 };

const CORD_META = `M${BROWSER.x} ${BROWSER.y + 14} C${BROWSER.x + 110} ${BROWSER.y + 62}, ${API.x - 110} ${API.y + 62}, ${API.x} ${API.y + 14}`;
const CORD_DB = `M${API.x} ${API.y + 14} C${API.x + 110} ${API.y + 56}, ${DB.x - 110} ${DB.y + 56}, ${DB.x} ${DB.y + 14}`;
const CORD_CTRL = `M${API.x + 14} ${API.y + 8} C${API.x + 150} ${API.y + 96}, ${STORE.x - 128} ${STORE.y - 74}, ${STORE.x - 12} ${STORE.y - 6}`;
const CORD_BYTES = `M${BROWSER.x - 6} ${BROWSER.y + 16} C${BROWSER.x - 40} ${BROWSER.y + 230}, ${API.x - 60} 318, ${API.x + 40} 318 C${STORE.x - 40} 318, ${STORE.x + 42} 312, ${STORE.x + 6} ${STORE.y + 18}`;

interface PatchBayProps {
  events: FlowEvent[];
  state: UploaderState;
}

/**
 * The routing, drawn as the patch bay it is.
 *
 * The heavy cord along the floor is the file. It leaves the browser jack and
 * lands on storage without ever passing through the API jack, which is the one
 * claim this whole project exists to make.
 */
export function PatchBay({ events, state }: PatchBayProps) {
  const [, tick] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => tick((value) => value + 1), 420);
    return () => clearInterval(timer);
  }, []);

  const now = Date.now();
  const recent = events.filter((event) => now - event.at < HOT_MS);
  const touches = (who: FlowEvent['actor']) =>
    recent.some((event) => event.actor === who || event.target === who);

  const bytesHot = state.parts.some((part) => part.state === 'uploading');
  const apiHot = touches('api') || touches('database');
  const dbHot = touches('database');
  const storeHot = bytesHot || touches('storage');

  return (
    <div className="patch">
      <svg viewBox="0 0 884 348" role="img" aria-label="Enrutamiento real de la subida">
        <Cord d={CORD_META} hot={apiHot} />
        <Cord d={CORD_DB} hot={dbHot} />
        <Cord d={CORD_CTRL} hot={storeHot && !bytesHot} />
        <Cord d={CORD_BYTES} hot={bytesHot} bytes />

        <text className="patch-note" x={BROWSER.x + 128} y={BROWSER.y + 56}>
          METADATOS · JSON
        </text>
        <text className="patch-note" x={API.x + 128} y={API.y + 52}>
          SESION · PARTES · CAS
        </text>
        <text className="patch-note" x={API.x + 96} y={API.y + 150}>
          PRESIGN · COMPLETE
        </text>
        <text className="patch-note patch-note--bytes" x={API.x - 138} y={338}>
          BYTES DEL ARCHIVO — NO TOCAN EL JACK DEL API
        </text>

        <Jack x={BROWSER.x} y={BROWSER.y} hot={bytesHot} label="NAVEGADOR" sub="File.slice → PUT" />
        <Jack x={API.x} y={API.y} hot={apiHot} label="API SPRING" sub="orquesta · sin bytes" />
        <Jack x={DB.x} y={DB.y} hot={dbHot} label="POSTGRES" sub="estado + etags" anchorEnd />
        <Jack x={STORE.x} y={STORE.y} hot={storeHot} label="MINIO / S3" sub="multipart real" anchorEnd />
      </svg>
    </div>
  );
}

function Cord({ d, hot, bytes }: { d: string; hot: boolean; bytes?: boolean }) {
  return (
    <>
      <path
        className={`cord ${bytes ? 'cord--bytes' : 'cord--signal'}${hot ? ' cord--hot' : ''}`}
        d={d}
      />
      {hot ? <path className={`cord-pulse${bytes ? ' cord-pulse--bytes' : ''}`} d={d} /> : null}
    </>
  );
}

function Jack({
  x,
  y,
  hot,
  label,
  sub,
  anchorEnd,
}: {
  x: number;
  y: number;
  hot: boolean;
  label: string;
  sub: string;
  anchorEnd?: boolean;
}) {
  const textX = anchorEnd ? x - 26 : x + 26;
  return (
    <g className={hot ? 'jack--hot' : undefined}>
      <circle className="jack-ring" cx={x} cy={y} r={13} />
      <circle className="jack-hole" cx={x} cy={y} r={6} />
      <text className="jack-label" x={textX} y={y - 1} textAnchor={anchorEnd ? 'end' : 'start'}>
        {label}
      </text>
      <text className="jack-sub" x={textX} y={y + 13} textAnchor={anchorEnd ? 'end' : 'start'}>
        {sub}
      </text>
    </g>
  );
}
