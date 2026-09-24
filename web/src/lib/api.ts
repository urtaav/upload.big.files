import type { EventSink } from './events';
import {
  ApiError,
  type ApiErrorBody,
  type DownloadUrlResponse,
  type UploadListResponse,
  type UploadStatus,
  type PartAckResponse,
  type PresignedPartsResponse,
  type UploadResponse,
} from './types';

export interface ApiConfig {
  baseUrl: string;
  userId: string;
  /** Injected so tests never touch the network. */
  fetchImpl?: typeof fetch;
  sink?: EventSink;
}

/**
 * Typed client for the upload orchestration API.
 *
 * Every call is timed and reported to the event sink, which is what makes the
 * network panel show the real cost of each step (including the repetition of
 * status polling).
 */
export class UploadApi {
  private readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;
  private readonly config: ApiConfig;

  constructor(config: ApiConfig) {
    this.config = config;
    this.baseUrl = config.baseUrl.replace(/\/$/, '');
    this.fetchImpl = config.fetchImpl ?? globalThis.fetch.bind(globalThis);
  }

  createUpload(
    input: { fileName: string; contentType: string; size: number },
    idempotencyKey: string,
  ): Promise<UploadResponse> {
    return this.json<UploadResponse>('POST', '/v1/uploads', {
      body: input,
      headers: { 'Idempotency-Key': idempotencyKey },
      actorTarget: 'database',
      label: 'Create upload session',
    });
  }

  presignParts(uploadId: string, partNumbers: number[]): Promise<PresignedPartsResponse> {
    return this.json<PresignedPartsResponse>('POST', `/v1/uploads/${uploadId}/parts`, {
      body: { partNumbers },
      actorTarget: 'storage',
      label: `Presign ${partNumbers.length} part(s)`,
    });
  }

  getUpload(uploadId: string, label = 'Poll upload status'): Promise<UploadResponse> {
    return this.json<UploadResponse>('GET', `/v1/uploads/${uploadId}`, {
      actorTarget: 'database',
      label,
    });
  }

  ackPart(
    uploadId: string,
    partNumber: number,
    etag: string,
    size: number,
  ): Promise<PartAckResponse> {
    return this.json<PartAckResponse>('POST', `/v1/uploads/${uploadId}/parts/${partNumber}/ack`, {
      body: { etag, size },
      actorTarget: 'database',
      label: `Acknowledge part ${partNumber}`,
      partNumber,
    });
  }

  complete(
    uploadId: string,
    parts: Array<{ partNumber: number; etag: string }>,
  ): Promise<UploadResponse> {
    return this.json<UploadResponse>('POST', `/v1/uploads/${uploadId}/complete`, {
      body: { parts },
      actorTarget: 'storage',
      label: 'Complete multipart upload',
    });
  }

  /** One page of the caller's uploads, newest first. */
  listUploads(params: { statuses?: UploadStatus[]; page?: number; size?: number } = {}) {
    const query = new URLSearchParams();
    if (params.statuses?.length) query.set('status', params.statuses.join(','));
    if (params.page !== undefined) query.set('page', String(params.page));
    if (params.size !== undefined) query.set('size', String(params.size));
    const suffix = query.toString() ? `?${query}` : '';
    return this.json<UploadListResponse>('GET', `/v1/uploads${suffix}`, {
      actorTarget: 'database',
      label: 'List uploads',
    });
  }

  downloadUrl(uploadId: string): Promise<DownloadUrlResponse> {
    return this.json<DownloadUrlResponse>('GET', `/v1/uploads/${uploadId}/download`, {
      actorTarget: 'storage',
      label: 'Sign download URL',
    });
  }

  cancel(uploadId: string): Promise<UploadResponse> {
    return this.json<UploadResponse>('DELETE', `/v1/uploads/${uploadId}`, {
      actorTarget: 'storage',
      label: 'Cancel upload session',
    });
  }

  private async json<T>(
    method: string,
    path: string,
    options: {
      body?: unknown;
      headers?: Record<string, string>;
      actorTarget: 'database' | 'storage';
      label: string;
      partNumber?: number;
    },
  ): Promise<T> {
    const url = `${this.baseUrl}${path}`;
    const headers: Record<string, string> = {
      'X-User-Id': this.config.userId,
      ...options.headers,
    };
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    this.config.sink?.({
      kind: 'request',
      actor: 'client',
      target: 'api',
      label: options.label,
      method,
      url: path,
      partNumber: options.partNumber,
    });

    const startedAt = performance.now();
    let response: Response;
    try {
      response = await this.fetchImpl(url, {
        method,
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      });
    } catch (cause) {
      this.config.sink?.({
        kind: 'response',
        actor: 'api',
        target: 'client',
        label: `${options.label} — network failure`,
        method,
        url: path,
        durationMs: Math.round(performance.now() - startedAt),
        failed: true,
        detail: cause instanceof Error ? cause.message : String(cause),
        partNumber: options.partNumber,
      });
      throw cause;
    }

    const durationMs = Math.round(performance.now() - startedAt);
    const text = await response.text();
    const parsed = text ? (JSON.parse(text) as unknown) : null;

    this.config.sink?.({
      kind: 'response',
      actor: options.actorTarget,
      target: 'client',
      label: options.label,
      method,
      url: path,
      httpStatus: response.status,
      durationMs,
      failed: !response.ok,
      partNumber: options.partNumber,
      detail: response.ok ? undefined : (parsed as ApiErrorBody | null)?.message,
    });

    if (!response.ok) {
      const body = parsed as ApiErrorBody | null;
      throw new ApiError(response.status, body, body?.message ?? `${method} ${path} failed`);
    }
    return parsed as T;
  }
}
