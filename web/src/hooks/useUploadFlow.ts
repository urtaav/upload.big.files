import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { UploadApi } from '../lib/api';
import { createEventLog, type FlowEvent } from '../lib/events';
import { Uploader, type UploaderState } from '../lib/uploader';
import type { UploadResponse, UploadSummary } from '../lib/types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';
const USER_ID = import.meta.env.VITE_USER_ID ?? '00000000-0000-0000-0000-000000000001';
const POLL_INTERVAL_MS = 2000;
/** How many times to keep polling after COMPLETED before making the point. */
const POLLS_AFTER_COMPLETION = 6;

/** States in which an existing session still accepts parts. */
const RESUMABLE_STATUSES = new Set<UploaderState['status']>(['uploading', 'paused', 'failed']);

const IDLE_STATE: UploaderState = {
  status: 'idle',
  parts: [],
  bytesSent: 0,
  totalBytes: 0,
};

export interface FlowSettings {
  concurrency: number;
  failPart: number | null;
}

/**
 * Wires the pure uploader to React: one uploader instance per run, one event
 * log for the whole session, and an independent status poll so the screen can
 * show the difference between what the browser believes and what the API
 * actually stores.
 */
export function useUploadFlow() {
  const [file, setFile] = useState<File | null>(null);
  const [state, setState] = useState<UploaderState>(IDLE_STATE);
  const [events, setEvents] = useState<FlowEvent[]>([]);
  const [serverSession, setServerSession] = useState<UploadResponse | null>(null);
  const [settings, setSettings] = useState<FlowSettings>({ concurrency: 3, failPart: null });
  const [error, setError] = useState<string | null>(null);

  const log = useMemo(() => createEventLog(), []);
  const api = useMemo(
    () => new UploadApi({ baseUrl: BASE_URL, userId: USER_ID, sink: log.emit }),
    [log],
  );
  const uploaderRef = useRef<Uploader | null>(null);
  const pollsAfterDone = useRef(0);

  useEffect(() => log.subscribe(setEvents), [log]);

  const newUploader = useCallback(
    (source: File) =>
      new Uploader({
        client: api,
        file: source,
        sink: log.emit,
        onState: setState,
        concurrency: settings.concurrency,
        failOnce: settings.failPart ? new Set([settings.failPart]) : undefined,
      }),
    [api, log, settings.concurrency, settings.failPart],
  );

  const selectFile = useCallback(
    (next: File | null) => {
      log.clear();
      setError(null);
      setServerSession(null);
      setState({ ...IDLE_STATE, totalBytes: next?.size ?? 0 });
      uploaderRef.current = null;
      pollsAfterDone.current = 0;
      setFile(next);
    },
    [log],
  );

  // `start` delegates to `resumeFromServer`, which is declared below it. A ref
  // breaks the cycle without making either depend on the other's identity.
  const resumeFromServerRef = useRef<(() => Promise<void>) | null>(null);

  /**
   * Starts the upload, or continues the one already open for this file.
   *
   * Retrying after a failure must never create a second session: the first one
   * still holds every part that did make it, and its multipart upload is still
   * open in storage. Creating a new one would abandon both.
   */
  const start = useCallback(async () => {
    if (!file) return;
    const openSession = state.uploadId && RESUMABLE_STATUSES.has(state.status);
    if (openSession) {
      await resumeFromServerRef.current?.();
      return;
    }

    setError(null);
    pollsAfterDone.current = 0;
    const uploader = newUploader(file);
    uploaderRef.current = uploader;
    try {
      await uploader.start();
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') return;
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }, [file, newUploader, state.status, state.uploadId]);

  /**
   * Throws away everything this browser knows and rebuilds the upload from the
   * API's own state. This is what a page reload would do, minus the file
   * handle, which no browser can persist.
   */
  const resumeFromServer = useCallback(async () => {
    const uploadId = state.uploadId ?? serverSession?.uploadId;
    if (!file || !uploadId) return;
    setError(null);
    log.emit({
      kind: 'note',
      actor: 'client',
      label: 'Local state discarded',
      detail: 'Rebuilding the upload from GET /v1/uploads/{id} alone.',
    });
    try {
      const session = await api.getUpload(uploadId, 'Read session state to resume');
      const uploader = newUploader(file);
      uploaderRef.current = uploader;
      await uploader.resume(session);
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') return;
      setError(cause instanceof Error ? cause.message : String(cause));
    }
  }, [api, file, log, newUploader, serverSession?.uploadId, state.uploadId]);

  resumeFromServerRef.current = resumeFromServer;

  /**
   * Continues a session picked from the explorer. The file itself cannot be
   * restored by any browser, so the user re-picks it and we verify it is the
   * same one before sending a single byte: a mismatch would corrupt the object
   * silently, since storage assembles whatever parts it is given.
   */
  const resumeFromLibrary = useCallback(
    async (summary: UploadSummary) => {
      if (!file) {
        setError('Elige primero el archivo original para poder reanudar esta subida.');
        return;
      }
      if (file.name !== summary.fileName || file.size !== summary.size) {
        setError(
          `El archivo elegido no coincide con la sesión: se esperaba "${summary.fileName}" de ${summary.size} bytes.`,
        );
        return;
      }

      setError(null);
      pollsAfterDone.current = 0;
      try {
        const session = await api.getUpload(summary.uploadId, 'Read session state to resume');
        const uploader = newUploader(file);
        uploaderRef.current = uploader;
        await uploader.resume(session);
      } catch (cause) {
        if (cause instanceof DOMException && cause.name === 'AbortError') return;
        setError(cause instanceof Error ? cause.message : String(cause));
      }
    },
    [api, file, newUploader],
  );

  const pause = useCallback(() => uploaderRef.current?.pause(), []);
  const unpause = useCallback(() => uploaderRef.current?.unpause(), []);
  const cancel = useCallback(async () => {
    await uploaderRef.current?.cancel();
  }, []);

  const reset = useCallback(() => selectFile(null), [selectFile]);

  // Independent status poll. It runs on its own clock, exactly like a real
  // client that has no push channel: every tick is a request in the log.
  useEffect(() => {
    const uploadId = state.uploadId;
    if (!uploadId) return;
    if (state.status === 'cancelled') return;

    let live = true;
    const tick = async () => {
      if (!live) return;
      if (state.status === 'done' && pollsAfterDone.current >= POLLS_AFTER_COMPLETION) {
        return;
      }
      try {
        const snapshot = await api.getUpload(uploadId);
        if (!live) return;
        setServerSession(snapshot);
        if (state.status === 'done') {
          pollsAfterDone.current += 1;
          if (pollsAfterDone.current === POLLS_AFTER_COMPLETION) {
            log.emit({
              kind: 'note',
              actor: 'client',
              label: `Polled ${POLLS_AFTER_COMPLETION} more times, still ${snapshot.status}`,
              detail:
                'Nothing is driving COMPLETED -> PROCESSING. The synchronous flow is over and no one took over.',
            });
          }
        }
      } catch {
        /* the log already recorded the failure */
      }
    };

    void tick();
    const timer = setInterval(tick, POLL_INTERVAL_MS);
    return () => {
      live = false;
      clearInterval(timer);
    };
  }, [api, log, state.status, state.uploadId]);

  return {
    api,
    config: { baseUrl: BASE_URL, userId: USER_ID },
    file,
    state,
    events,
    serverSession,
    settings,
    error,
    setSettings,
    selectFile,
    start,
    pause,
    unpause,
    cancel,
    resumeFromServer,
    resumeFromLibrary,
    reset,
  };
}
