import { useEffect, useMemo, useRef, useState } from 'react';
import type { FlowEvent } from '../lib/events';
import { formatBytes, formatClock, formatDuration } from '../lib/format';

type Filter = 'todo' | 'api' | 'storage' | 'sondeo';

const FILTERS: Filter[] = ['todo', 'api', 'storage', 'sondeo'];
const MAX_STRIPS = 300;

function matches(event: FlowEvent, filter: Filter): boolean {
  if (filter === 'todo') return true;
  if (filter === 'sondeo') return event.label.startsWith('Poll upload status');
  if (filter === 'storage') return event.actor === 'storage' || event.target === 'storage';
  return event.actor === 'api' || event.target === 'api' || event.actor === 'database';
}

export function FaultLog({ events }: { events: FlowEvent[] }) {
  const [filter, setFilter] = useState<Filter>('todo');
  const [follow, setFollow] = useState(true);
  const boxRef = useRef<HTMLDivElement>(null);

  const strips = useMemo(
    () => events.filter((event) => matches(event, filter)).slice(-MAX_STRIPS),
    [events, filter],
  );

  useEffect(() => {
    if (!follow || !boxRef.current) return;
    boxRef.current.scrollTop = boxRef.current.scrollHeight;
  }, [strips, follow]);

  return (
    <>
      <div className="selector" style={{ margin: '0 20px 12px' }}>
        {FILTERS.map((option) => (
          <button key={option} data-on={filter === option} onClick={() => setFilter(option)}>
            {option}
          </button>
        ))}
        <button data-on={follow} onClick={() => setFollow((value) => !value)}>
          seguir
        </button>
      </div>

      <div
        className="log"
        ref={boxRef}
        onScroll={(event) => {
          const box = event.currentTarget;
          const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 24;
          if (atBottom !== follow) setFollow(atBottom);
        }}
      >
        {strips.length === 0 ? (
          <div className="log__strip">
            <span className="log__t">--:--:--</span>
            <span className="log__verb" />
            <span className="log__msg" style={{ color: 'var(--silkscreen-faint)' }}>
              sin tráfico
            </span>
            <span className="log__val" />
          </div>
        ) : null}

        {strips.map((event) => {
          const tone = event.failed
            ? ' log__strip--fault'
            : event.kind === 'note'
              ? ' log__strip--mark'
              : event.actor === 'storage' || event.target === 'storage'
                ? ' log__strip--storage'
                : '';
          return (
            <div className={`log__strip${tone}`} key={event.id}>
              <span className="log__t">{formatClock(event.at)}</span>
              <span className="log__verb">{event.method ?? event.kind.slice(0, 4)}</span>
              <span className="log__msg">
                {event.label}
                {event.detail ? <small>{event.detail}</small> : null}
              </span>
              <span className="log__val">
                {event.httpStatus ? `${event.httpStatus} ` : ''}
                {event.durationMs !== undefined ? formatDuration(event.durationMs) : ''}
                {event.bytes ? ` ${formatBytes(event.bytes, 0)}` : ''}
              </span>
            </div>
          );
        })}
      </div>
    </>
  );
}
