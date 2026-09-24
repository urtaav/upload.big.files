/**
 * Every meaningful thing that happens during an upload is emitted here: the
 * timeline, the network log and the flow diagram all read from this one
 * stream. Nothing in the UI observes the uploader directly.
 */

/** Who is doing the work. The flow diagram lights up the matching node. */
export type Actor = 'client' | 'api' | 'database' | 'storage';

export type FlowEventKind =
  | 'request' // an HTTP call left the browser
  | 'response' // it came back
  | 'lifecycle' // the upload session changed state
  | 'part' // a part changed state
  | 'note'; // an explanatory marker, e.g. the post-completion handoff

export interface FlowEvent {
  id: number;
  at: number;
  kind: FlowEventKind;
  actor: Actor;
  target?: Actor;
  label: string;
  detail?: string;
  method?: string;
  url?: string;
  httpStatus?: number;
  durationMs?: number;
  bytes?: number;
  partNumber?: number;
  failed?: boolean;
}

export type EventSink = (event: Omit<FlowEvent, 'id' | 'at'>) => void;

/** Monotonic ids so React keys stay stable while the log grows. */
export function createEventLog() {
  let nextId = 1;
  const events: FlowEvent[] = [];
  const listeners = new Set<(events: FlowEvent[]) => void>();

  const emit: EventSink = (event) => {
    events.push({ ...event, id: nextId++, at: Date.now() });
    const snapshot = [...events];
    listeners.forEach((listener) => listener(snapshot));
  };

  return {
    emit,
    subscribe(listener: (events: FlowEvent[]) => void) {
      listeners.add(listener);
      listener([...events]);
      return () => void listeners.delete(listener);
    },
    clear() {
      events.length = 0;
      listeners.forEach((listener) => listener([]));
    },
  };
}

export type EventLog = ReturnType<typeof createEventLog>;
