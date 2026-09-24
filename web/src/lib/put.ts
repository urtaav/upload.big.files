/**
 * The one place that talks to object storage. It uses XMLHttpRequest instead of
 * fetch for a single reason: fetch cannot report upload progress, and watching
 * bytes leave the browser is the whole point of this screen.
 */

export interface PutRequest {
  url: string;
  body: Blob;
  partNumber: number;
  signal: AbortSignal;
  onProgress: (sentBytes: number) => void;
}

export interface PutResult {
  etag: string;
  httpStatus: number;
}

export type PutFn = (request: PutRequest) => Promise<PutResult>;

export class StoragePutError extends Error {
  readonly httpStatus: number;
  readonly partNumber: number;

  constructor(httpStatus: number, partNumber: number, message: string) {
    super(message);
    this.name = 'StoragePutError';
    this.httpStatus = httpStatus;
    this.partNumber = partNumber;
  }
}

export const xhrPut: PutFn = ({ url, body, partNumber, signal, onProgress }) =>
  new Promise<PutResult>((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open('PUT', url, true);

    request.upload.addEventListener('progress', (event) => onProgress(event.loaded));

    request.addEventListener('load', () => {
      if (request.status < 200 || request.status >= 300) {
        reject(
          new StoragePutError(
            request.status,
            partNumber,
            `Storage rejected part ${partNumber} with HTTP ${request.status}`,
          ),
        );
        return;
      }
      const etag = request.getResponseHeader('ETag');
      if (!etag) {
        // Almost always a CORS problem: the response arrived but the browser is
        // not allowed to read the header the completion request depends on.
        reject(
          new StoragePutError(
            request.status,
            partNumber,
            `Part ${partNumber} uploaded but its ETag header is not readable. ` +
              'Check the CORS configuration of the storage endpoint.',
          ),
        );
        return;
      }
      onProgress(body.size);
      resolve({ etag, httpStatus: request.status });
    });

    request.addEventListener('error', () =>
      reject(new StoragePutError(0, partNumber, `Network error uploading part ${partNumber}`)),
    );
    request.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));

    signal.addEventListener('abort', () => request.abort(), { once: true });
    request.send(body);
  });
