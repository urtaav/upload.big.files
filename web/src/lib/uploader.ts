import type { EventSink } from './events';
import { StoragePutError, xhrPut, type PutFn } from './put';
import type { UploadApi } from './api';
import type { UploadResponse } from './types';

export type UploadClient = Pick<
  UploadApi,
  'createUpload' | 'presignParts' | 'getUpload' | 'ackPart' | 'complete' | 'cancel'
>;

export type PartState = 'pending' | 'uploading' | 'uploaded' | 'failed';

export interface PartProgress {
  partNumber: number;
  offset: number;
  size: number;
  state: PartState;
  sentBytes: number;
  attempts: number;
  etag?: string;
  /** Set when the part was already stored before this session started. */
  restored: boolean;
  error?: string;
}

export type UploaderStatus =
  | 'idle'
  | 'creating'
  | 'uploading'
  | 'paused'
  | 'completing'
  | 'done'
  | 'failed'
  | 'cancelled';

export interface UploaderState {
  status: UploaderStatus;
  uploadId?: string;
  session?: UploadResponse;
  parts: PartProgress[];
  bytesSent: number;
  totalBytes: number;
  startedAt?: number;
  finishedAt?: number;
  error?: string;
}

export interface UploaderOptions {
  client: UploadClient;
  file: File;
  concurrency?: number;
  maxRetries?: number;
  put?: PutFn;
  sink?: EventSink;
  onState?: (state: UploaderState) => void;
  retryDelayMs?: (attempt: number) => number;
  /** How many presigned URLs to request per round trip. */
  presignBatchSize?: number;
  /**
   * Part numbers whose first attempt is forced to fail. Purely a teaching
   * control: it makes the retry path observable on demand.
   */
  failOnce?: Set<number>;
}

const DEFAULT_RETRY_DELAY = (attempt: number) => Math.min(8_000, 2 ** attempt * 250);
const DEFAULT_PRESIGN_BATCH = 12;
/** A signature can be refreshed this many times per part before giving up. */
const MAX_SIGN_REFRESHES = 2;

/**
 * Drives one file from "nothing" to a COMPLETED upload session.
 *
 * It holds no React state and touches no DOM: it takes a client, a PUT
 * function and a file, and reports everything it does through `onState` and
 * the event sink. That is what makes it testable without a browser.
 *
 * The file is never read whole. Each part is a `Blob` view produced by
 * `File.slice`, so a 5 GiB upload costs the same memory as a 5 MiB one.
 */
export class Uploader {
  private readonly client: UploadClient;
  private readonly file: File;
  private readonly concurrency: number;
  private readonly maxRetries: number;
  private readonly put: PutFn;
  private readonly sink?: EventSink;
  private readonly onState?: (state: UploaderState) => void;
  private readonly retryDelayMs: (attempt: number) => number;
  private readonly presignBatchSize: number;
  private readonly failOnce: Set<number>;

  /** Presigned URLs held only as long as they are worth holding. */
  private urls = new Map<number, string>();
  private signing = new Map<number, Promise<void>>();
  private pending: number[] = [];
  private controllers = new Map<number, AbortController>();
  private pausePromise: Promise<void> | null = null;
  private releasePause: (() => void) | null = null;
  private cancelled = false;

  state: UploaderState;

  constructor(options: UploaderOptions) {
    this.client = options.client;
    this.file = options.file;
    this.concurrency = options.concurrency ?? 3;
    this.maxRetries = options.maxRetries ?? 3;
    this.put = options.put ?? xhrPut;
    this.sink = options.sink;
    this.onState = options.onState;
    this.retryDelayMs = options.retryDelayMs ?? DEFAULT_RETRY_DELAY;
    this.presignBatchSize = options.presignBatchSize ?? DEFAULT_PRESIGN_BATCH;
    this.failOnce = options.failOnce ?? new Set();
    this.state = {
      status: 'idle',
      parts: [],
      bytesSent: 0,
      totalBytes: options.file.size,
    };
  }

  /** Creates a session for this file and uploads it end to end. */
  async start(idempotencyKey = crypto.randomUUID()): Promise<UploadResponse> {
    this.patch({ status: 'creating', startedAt: Date.now() });
    const session = await this.client.createUpload(
      {
        fileName: this.file.name,
        contentType: this.file.type || 'application/octet-stream',
        size: this.file.size,
      },
      idempotencyKey,
    );
    return this.run(session);
  }

  /** Continues an existing session, re-uploading only what is missing. */
  async resume(session: UploadResponse): Promise<UploadResponse> {
    this.patch({ status: 'uploading', startedAt: this.state.startedAt ?? Date.now() });
    this.note(
      `Resuming session ${session.uploadId.slice(0, 8)}`,
      `${session.parts.length} of ${session.totalParts} parts are already stored. That list came from the API, not from this browser.`,
    );
    return this.run(session);
  }

  pause() {
    if (this.state.status !== 'uploading' || this.pausePromise) return;
    this.pausePromise = new Promise((resolve) => {
      this.releasePause = resolve;
    });
    this.patch({ status: 'paused' });
    this.note('Paused', 'Parts in flight finish; no new part starts.');
  }

  unpause() {
    if (!this.pausePromise) return;
    this.releasePause?.();
    this.pausePromise = null;
    this.releasePause = null;
    this.patch({ status: 'uploading' });
    this.note('Resumed');
  }

  /** Aborts everything in flight and asks the API to abort the multipart upload. */
  async cancel(): Promise<void> {
    this.cancelled = true;
    this.unpause();
    this.controllers.forEach((controller) => controller.abort());
    this.controllers.clear();
    const uploadId = this.state.uploadId;
    this.patch({ status: 'cancelled', finishedAt: Date.now() });
    if (uploadId) {
      await this.client.cancel(uploadId).catch(() => undefined);
    }
  }

  private async run(session: UploadResponse): Promise<UploadResponse> {
    const parts = this.planParts(session);
    this.patch({ status: 'uploading', uploadId: session.uploadId, session, parts });
    this.recomputeBytes();

    const missing = parts.filter((part) => part.state !== 'uploaded').map((part) => part.partNumber);

    try {
      if (missing.length > 0) {
        await this.uploadAll(session.uploadId, missing);
      }

      if (this.cancelled) throw new DOMException('aborted', 'AbortError');

      this.patch({ status: 'completing' });
      const completed = await this.client.complete(
        session.uploadId,
        this.state.parts
          .slice()
          .sort((a, b) => a.partNumber - b.partNumber)
          .map((part) => ({ partNumber: part.partNumber, etag: part.etag as string })),
      );

      this.patch({ status: 'done', session: completed, finishedAt: Date.now() });
      this.sink?.({
        kind: 'lifecycle',
        actor: 'api',
        label: `Upload ${completed.status}`,
        detail: 'The object exists in storage. Everything synchronous is over.',
      });
      return completed;
    } catch (error) {
      if (this.cancelled) {
        throw error;
      }
      this.patch({
        status: 'failed',
        finishedAt: Date.now(),
        error: error instanceof Error ? error.message : String(error),
      });
      throw error;
    }
  }

  /**
   * Part boundaries come from the API, never from the client: the server
   * decided the part size when it created the session.
   */
  private planParts(session: UploadResponse): PartProgress[] {
    const stored = new Map(session.parts.map((part) => [part.partNumber, part]));
    return Array.from({ length: session.totalParts }, (_, index) => {
      const partNumber = index + 1;
      const offset = index * session.partSize;
      const size = Math.min(session.partSize, this.file.size - offset);
      const alreadyStored = stored.get(partNumber);
      return {
        partNumber,
        offset,
        size,
        state: alreadyStored ? ('uploaded' as const) : ('pending' as const),
        sentBytes: alreadyStored ? size : 0,
        attempts: 0,
        etag: alreadyStored?.etag,
        restored: Boolean(alreadyStored),
      };
    });
  }

  /** A fixed-size worker pool draining a queue of part numbers. */
  private async uploadAll(uploadId: string, queue: number[]) {
    this.urls.clear();
    this.pending = [...queue];
    const workers = Array.from(
      { length: Math.min(this.concurrency, this.pending.length) },
      async () => {
        while (this.pending.length > 0) {
          if (this.cancelled) return;
          if (this.pausePromise) await this.pausePromise;
          const partNumber = this.pending.shift();
          if (partNumber === undefined) return;
          await this.uploadPart(uploadId, partNumber);
        }
      },
    );
    await Promise.all(workers);
  }

  /**
   * Returns a usable URL for a part, signing a batch of upcoming parts in the
   * same round trip.
   *
   * Signatures expire (15 minutes by default). Signing every part upfront means
   * the tail of a slow upload arrives with dead URLs, so they are requested
   * close to the moment they are used and refreshed when storage rejects them.
   */
  private async urlFor(uploadId: string, partNumber: number): Promise<string> {
    for (;;) {
      const cached = this.urls.get(partNumber);
      if (cached) return cached;

      // Another worker is already signing a batch that covers this part;
      // waiting for it beats asking the API for the same URL twice.
      const inFlight = this.signing.get(partNumber);
      if (inFlight) {
        await inFlight;
        if (this.urls.has(partNumber)) continue;
      }

      const batch = [partNumber];
      for (const candidate of this.pending) {
        if (batch.length >= this.presignBatchSize) break;
        if (!this.urls.has(candidate) && !this.signing.has(candidate) && !batch.includes(candidate)) {
          batch.push(candidate);
        }
      }

      const request = this.client.presignParts(uploadId, batch).then((presigned) => {
        presigned.parts.forEach((part) => this.urls.set(part.partNumber, part.uploadUrl));
      });
      batch.forEach((number) => this.signing.set(number, request));
      try {
        await request;
      } finally {
        batch.forEach((number) => this.signing.delete(number));
      }

      const url = this.urls.get(partNumber);
      if (!url) {
        throw new Error(`The API did not return a presigned URL for part ${partNumber}`);
      }
      return url;
    }
  }

  private async uploadPart(uploadId: string, partNumber: number) {
    const part = this.partAt(partNumber);
    const blob = this.file.slice(part.offset, part.offset + part.size);
    let signRefreshes = 0;

    for (let attempt = 1; attempt <= this.maxRetries; attempt += 1) {
      if (this.cancelled) throw new DOMException('aborted', 'AbortError');

      const url = await this.urlFor(uploadId, partNumber);
      const controller = new AbortController();
      this.controllers.set(partNumber, controller);
      this.updatePart(partNumber, {
        state: 'uploading',
        attempts: attempt,
        sentBytes: 0,
        error: undefined,
      });
      this.sink?.({
        kind: 'part',
        actor: 'client',
        target: 'storage',
        label: attempt === 1 ? `PUT part ${partNumber}` : `PUT part ${partNumber} — retry ${attempt - 1}`,
        method: 'PUT',
        url: 'presigned storage URL',
        partNumber,
        bytes: part.size,
      });

      const startedAt = performance.now();
      try {
        if (attempt === 1 && this.failOnce.has(partNumber)) {
          this.failOnce.delete(partNumber);
          throw new Error(`Injected failure on part ${partNumber}`);
        }
        const result = await this.put({
          url,
          body: blob,
          partNumber,
          signal: controller.signal,
          onProgress: (sent) => this.updatePart(partNumber, { sentBytes: Math.min(sent, part.size) }),
        });
        this.controllers.delete(partNumber);

        this.sink?.({
          kind: 'part',
          actor: 'storage',
          target: 'client',
          label: `Part ${partNumber} stored`,
          method: 'PUT',
          httpStatus: result.httpStatus,
          durationMs: Math.round(performance.now() - startedAt),
          partNumber,
          bytes: part.size,
        });

        await this.client.ackPart(uploadId, partNumber, result.etag, part.size);
        this.updatePart(partNumber, { state: 'uploaded', etag: result.etag, sentBytes: part.size });
        return;
      } catch (error) {
        this.controllers.delete(partNumber);
        if (this.cancelled || (error instanceof DOMException && error.name === 'AbortError')) {
          throw error;
        }
        const message = error instanceof Error ? error.message : String(error);

        // A 403 from storage means the signature, not the transfer, was
        // rejected. Refreshing the URL is not a retry of a failed upload, so it
        // does not consume one of the part's attempts.
        const rejectedSignature = error instanceof StoragePutError && error.httpStatus === 403;
        if (rejectedSignature && signRefreshes < MAX_SIGN_REFRESHES) {
          signRefreshes += 1;
          this.urls.delete(partNumber);
          attempt -= 1;
          this.updatePart(partNumber, { state: 'pending', sentBytes: 0 });
          this.sink?.({
            kind: 'part',
            actor: 'storage',
            target: 'client',
            label: `Part ${partNumber}: signature rejected, re-signing`,
            partNumber,
            detail: message,
          });
          continue;
        }

        const lastAttempt = attempt === this.maxRetries;
        this.updatePart(partNumber, {
          state: lastAttempt ? 'failed' : 'pending',
          sentBytes: 0,
          error: message,
        });
        this.sink?.({
          kind: 'part',
          actor: 'storage',
          target: 'client',
          label: lastAttempt
            ? `Part ${partNumber} failed after ${attempt} attempts`
            : `Part ${partNumber} failed, retrying`,
          partNumber,
          failed: true,
          durationMs: Math.round(performance.now() - startedAt),
          detail: message,
        });
        if (lastAttempt) throw error;
        await sleep(this.retryDelayMs(attempt));
      }
    }
  }

  private partAt(partNumber: number): PartProgress {
    return this.state.parts[partNumber - 1];
  }

  private updatePart(partNumber: number, patch: Partial<PartProgress>) {
    const parts = this.state.parts.slice();
    parts[partNumber - 1] = { ...parts[partNumber - 1], ...patch };
    this.state = { ...this.state, parts };
    this.recomputeBytes();
  }

  private recomputeBytes() {
    const bytesSent = this.state.parts.reduce((total, part) => total + part.sentBytes, 0);
    this.state = { ...this.state, bytesSent };
    this.onState?.(this.state);
  }

  private patch(patch: Partial<UploaderState>) {
    this.state = { ...this.state, ...patch };
    this.onState?.(this.state);
  }

  private note(label: string, detail?: string) {
    this.sink?.({ kind: 'note', actor: 'client', label, detail });
  }
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
