import { useEffect, useRef } from 'react';
import type { PartProgress } from '../lib/uploader';

/* Real programme meters do not track their input linearly: they rise almost
   instantly and fall back slowly, which is what makes a transfer legible as
   movement rather than as a bar. These are the ballistics. */
const ATTACK = 0.5;
const DECAY = 0.055;
const PEAK_FALL = 0.0035;

/** Past this many parts the numbers stop fitting, so the bridge goes hairline. */
const DENSE_AT = 48;

function stateClass(part: PartProgress): string {
  if (part.state === 'uploading') return ' channel--flight';
  if (part.state === 'failed') return ' channel--fault';
  if (part.state === 'uploaded') return part.restored ? ' channel--restored' : ' channel--stored';
  return '';
}

function title(part: PartProgress): string {
  const rows = [
    `CH ${part.partNumber}`,
    `${(part.size / 1024 / 1024).toFixed(1)} MiB`,
    part.state === 'uploading'
      ? 'ON AIR'
      : part.state === 'failed'
        ? 'FAULT'
        : part.state === 'uploaded'
          ? part.restored
            ? 'PLAYBACK — recuperada del API'
            : 'READY'
          : 'en espera',
    `intentos ${part.attempts}`,
  ];
  if (part.etag) rows.push(`etag ${part.etag}`);
  if (part.error) rows.push(part.error);
  return rows.join('\n');
}

interface MeterBridgeProps {
  parts: PartProgress[];
  /**
   * Changes when a session is created. Real equipment slams every indicator to
   * full scale at power-up to prove the meters work; the bridge does the same,
   * and the ballistics above carry it back down on their own.
   */
  selfTest?: string;
}

export function MeterBridge({ parts, selfTest }: MeterBridgeProps) {
  const dense = parts.length > DENSE_AT;
  const partsRef = useRef(parts);
  partsRef.current = parts;

  const levelNodes = useRef<(HTMLDivElement | null)[]>([]);
  const peakNodes = useRef<(HTMLDivElement | null)[]>([]);
  const levels = useRef<number[]>([]);
  const peaks = useRef<number[]>([]);

  useEffect(() => {
    if (!selfTest) return;
    levels.current = partsRef.current.map(() => 1);
    peaks.current = partsRef.current.map(() => 1);
  }, [selfTest]);

  // The meters animate outside React: at five hundred channels, a state update
  // per frame would cost more than the transfer being measured.
  useEffect(() => {
    let frame = 0;
    const tick = () => {
      const current = partsRef.current;
      for (let i = 0; i < current.length; i += 1) {
        const part = current[i];
        const target =
          part.state === 'uploaded' ? 1 : part.size > 0 ? part.sentBytes / part.size : 0;

        const level = levels.current[i] ?? 0;
        const next = level + (target - level) * (target > level ? ATTACK : DECAY);
        levels.current[i] = next;

        const peak = peaks.current[i] ?? 0;
        peaks.current[i] = next > peak ? next : Math.max(next, peak - PEAK_FALL);

        const levelNode = levelNodes.current[i];
        if (levelNode) levelNode.style.height = `${(next * 100).toFixed(2)}%`;

        const peakNode = peakNodes.current[i];
        if (peakNode) {
          const visible = peaks.current[i] > 0.01 && part.state !== 'pending';
          peakNode.style.opacity = visible ? '1' : '0';
          peakNode.style.bottom = `${(peaks.current[i] * 100).toFixed(2)}%`;
        }
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, []);

  if (parts.length === 0) {
    return (
      <div className="well bridge">
        <span className="bridge__blank">sin canales · monta una cinta</span>
      </div>
    );
  }

  return (
    <div className={`well bridge${dense ? ' bridge--dense' : ''}`}>
      {parts.map((part, index) => (
        <div
          className={`channel${stateClass(part)}`}
          key={part.partNumber}
          title={title(part)}
        >
          <div className="channel__lamp" />
          <div className="channel__meter">
            <div
              className="channel__level"
              ref={(node) => {
                levelNodes.current[index] = node;
              }}
              style={{ height: 0 }}
            />
            <div
              className="channel__peak"
              ref={(node) => {
                peakNodes.current[index] = node;
              }}
              style={{ opacity: 0, bottom: 0 }}
            />
          </div>
          <div className="channel__no">{part.partNumber}</div>
        </div>
      ))}
    </div>
  );
}
