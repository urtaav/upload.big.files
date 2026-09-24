import { describe, expect, it, vi } from 'vitest';
import { Uploader, type UploadClient } from '../uploader';
import { StoragePutError, type PutFn } from '../put';
import type { UploadResponse, UploadPartResponse } from '../types';

const PART_SIZE = 10;

function session(overrides: Partial<UploadResponse> = {}): UploadResponse {
  const size = overrides.size ?? 25;
  return {
    uploadId: 'upload-1',
    fileName: 'clip.mp4',
    contentType: 'video/mp4',
    size,
    objectKey: 'videos/upload-1/original.mp4',
    status: 'CREATED',
    partSize: PART_SIZE,
    totalParts: Math.max(1, Math.ceil(size / PART_SIZE)),
    uploadedParts: 0,
    parts: [],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    expiresAt: '2026-01-02T00:00:00Z',
    ...overrides,
  };
}

function fileOf(size: number): File {
  return new File([new Uint8Array(size)], 'clip.mp4', { type: 'video/mp4' });
}

function stubClient(initial: UploadResponse, overrides: Partial<UploadClient> = {}): UploadClient {
  return {
    createUpload: vi.fn(async () => initial),
    presignParts: vi.fn(async (uploadId: string, partNumbers: number[]) => ({
      uploadId,
      parts: partNumbers.map((partNumber) => ({
        partNumber,
        uploadUrl: `https://storage.test/part/${partNumber}`,
        expiresAt: '2026-01-01T00:15:00Z',
      })),
    })),
    getUpload: vi.fn(async () => initial),
    ackPart: vi.fn(async (uploadId: string, partNumber: number, etag: string) => ({
      uploadId,
      partNumber,
      etag,
      uploadedParts: partNumber,
    })),
    complete: vi.fn(async () => ({ ...initial, status: 'COMPLETED' as const })),
    cancel: vi.fn(async () => ({ ...initial, status: 'CANCELLED' as const })),
    ...overrides,
  };
}

const okPut: PutFn = async ({ partNumber, body, onProgress }) => {
  onProgress(body.size);
  return { etag: `"etag-${partNumber}"`, httpStatus: 200 };
};

function uploaderFor(
  file: File,
  client: UploadClient,
  put: PutFn,
  options: { concurrency?: number; maxRetries?: number; presignBatchSize?: number } = {},
) {
  return new Uploader({
    client,
    file,
    put,
    concurrency: options.concurrency ?? 3,
    maxRetries: options.maxRetries ?? 3,
    presignBatchSize: options.presignBatchSize,
    retryDelayMs: () => 0,
  });
}

/** Part numbers passed to each presignParts call, in order. */
function presignCalls(client: UploadClient): number[][] {
  return (client.presignParts as ReturnType<typeof vi.fn>).mock.calls.map((call) => call[1]);
}

describe('Uploader', () => {
  it('slices the file into parts of the size the API dictates, last one shorter', async () => {
    const client = stubClient(session({ size: 25 }));
    const sizes: number[] = [];
    const uploader = uploaderFor(fileOf(25), client, async (req) => {
      sizes[req.partNumber - 1] = req.body.size;
      return okPut(req);
    });

    await uploader.start();

    expect(sizes).toEqual([10, 10, 5]);
  });

  it('never keeps more parts in flight than the configured concurrency', async () => {
    const client = stubClient(session({ size: 100 }));
    let inFlight = 0;
    let peak = 0;
    const uploader = uploaderFor(
      fileOf(100),
      client,
      async (req) => {
        inFlight += 1;
        peak = Math.max(peak, inFlight);
        await new Promise((resolve) => setTimeout(resolve, 1));
        inFlight -= 1;
        return okPut(req);
      },
      { concurrency: 2 },
    );

    await uploader.start();

    expect(peak).toBe(2);
  });

  it('retries a failed part and acknowledges it only once it succeeds', async () => {
    const client = stubClient(session({ size: 20 }));
    let attemptsOnPartTwo = 0;
    const uploader = uploaderFor(fileOf(20), client, async (req) => {
      if (req.partNumber === 2) {
        attemptsOnPartTwo += 1;
        if (attemptsOnPartTwo < 3) {
          throw new Error('storage refused the part');
        }
      }
      return okPut(req);
    });

    await uploader.start();

    expect(attemptsOnPartTwo).toBe(3);
    const ackedParts = (client.ackPart as ReturnType<typeof vi.fn>).mock.calls.map((call) => call[1]);
    expect(ackedParts.sort()).toEqual([1, 2]);
  });

  it('gives up on a part after maxRetries and fails the upload', async () => {
    const client = stubClient(session({ size: 20 }));
    const uploader = uploaderFor(
      fileOf(20),
      client,
      async (req) => {
        if (req.partNumber === 2) throw new Error('storage is down');
        return okPut(req);
      },
      { maxRetries: 2 },
    );

    await expect(uploader.start()).rejects.toThrow('storage is down');
    expect(uploader.state.status).toBe('failed');
    expect(client.complete).not.toHaveBeenCalled();
  });

  it('resumes from server state: re-uploads only missing parts, reuses stored ETags', async () => {
    const acked: UploadPartResponse[] = [
      { partNumber: 1, etag: '"etag-1"', size: 10, uploadedAt: '2026-01-01T00:00:01Z' },
    ];
    const existing = session({ size: 25, status: 'UPLOADING', uploadedParts: 1, parts: acked });
    const client = stubClient(existing);
    const uploadedNumbers: number[] = [];
    const uploader = uploaderFor(fileOf(25), client, async (req) => {
      uploadedNumbers.push(req.partNumber);
      return okPut(req);
    });

    await uploader.resume(existing);

    expect(uploadedNumbers.sort()).toEqual([2, 3]);
    expect(client.createUpload).not.toHaveBeenCalled();
    expect(presignCalls(client).flat().sort()).toEqual([2, 3]);
    expect(client.complete).toHaveBeenCalledWith('upload-1', [
      { partNumber: 1, etag: '"etag-1"' },
      { partNumber: 2, etag: '"etag-2"' },
      { partNumber: 3, etag: '"etag-3"' },
    ]);
  });

  it('sends every part to complete, ordered by part number', async () => {
    const client = stubClient(session({ size: 30 }));
    const uploader = uploaderFor(fileOf(30), client, okPut);

    await uploader.start();

    expect(client.complete).toHaveBeenCalledWith('upload-1', [
      { partNumber: 1, etag: '"etag-1"' },
      { partNumber: 2, etag: '"etag-2"' },
      { partNumber: 3, etag: '"etag-3"' },
    ]);
  });

  it('asks for presigned URLs in batches instead of signing every part upfront', async () => {
    // Presigned URLs expire (15 min by default). Signing 500 parts at once
    // guarantees the tail of a slow upload arrives with dead signatures.
    const client = stubClient(session({ size: 200 })); // 20 parts
    const uploader = uploaderFor(fileOf(200), client, okPut, {
      concurrency: 2,
      presignBatchSize: 5,
    });

    await uploader.start();

    const calls = presignCalls(client);
    expect(calls.length).toBeGreaterThan(1);
    calls.forEach((batch) => expect(batch.length).toBeLessThanOrEqual(5));
    expect(calls.flat().sort((a, b) => a - b)).toEqual(
      Array.from({ length: 20 }, (_, index) => index + 1),
    );
  });

  it('re-signs and retries when storage rejects an expired signature', async () => {
    const client = stubClient(session({ size: 10 }));
    let attempts = 0;
    const uploader = uploaderFor(
      fileOf(10),
      client,
      async (req) => {
        attempts += 1;
        if (attempts === 1) {
          throw new StoragePutError(403, req.partNumber, 'Request has expired');
        }
        return okPut(req);
      },
      { maxRetries: 1 },
    );

    await uploader.start();

    // maxRetries is 1, so the upload only survives because re-signing does not
    // burn an attempt: the first failure was the URL, not the transfer.
    expect(attempts).toBe(2);
    expect(presignCalls(client)).toEqual([[1], [1]]);
    expect(uploader.state.status).toBe('done');
  });

  it('gives up re-signing instead of looping forever on a permanent 403', async () => {
    const client = stubClient(session({ size: 10 }));
    let attempts = 0;
    const uploader = uploaderFor(
      fileOf(10),
      client,
      async (req) => {
        attempts += 1;
        throw new StoragePutError(403, req.partNumber, 'Access denied');
      },
      { maxRetries: 2 },
    );

    await expect(uploader.start()).rejects.toThrow();
    expect(attempts).toBeLessThanOrEqual(6);
    expect(uploader.state.status).toBe('failed');
  });

  it('cancel aborts parts in flight and leaves the upload cancelled', async () => {
    const client = stubClient(session({ size: 100 }));
    const uploader = uploaderFor(
      fileOf(100),
      client,
      (req) =>
        new Promise((_resolve, reject) => {
          req.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
        }),
      { concurrency: 2 },
    );

    const running = uploader.start();
    await new Promise((resolve) => setTimeout(resolve, 5));
    await uploader.cancel();
    await expect(running).rejects.toThrow();

    expect(uploader.state.status).toBe('cancelled');
    expect(client.cancel).toHaveBeenCalledWith('upload-1');
  });

  it('reports progress as bytes actually sent, not as parts finished', async () => {
    const client = stubClient(session({ size: 25 }));
    const seen: number[] = [];
    const uploader = new Uploader({
      client,
      file: fileOf(25),
      concurrency: 1,
      maxRetries: 1,
      retryDelayMs: () => 0,
      put: async ({ body, onProgress, partNumber }) => {
        onProgress(body.size / 2);
        onProgress(body.size);
        return { etag: `"etag-${partNumber}"`, httpStatus: 200 };
      },
      onState: (state) => seen.push(state.bytesSent),
    });

    await uploader.start();

    expect(seen).toContain(5);
    expect(seen.at(-1)).toBe(25);
  });
});
