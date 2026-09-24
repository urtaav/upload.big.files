/** Mirrors the API contracts in `com.videoflow.upload.interfaces.upload.dto`. */

export type UploadStatus =
  | 'CREATED'
  | 'UPLOADING'
  | 'COMPLETING'
  | 'COMPLETED'
  | 'PROCESSING'
  | 'READY'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED';

export interface UploadPartResponse {
  partNumber: number;
  etag: string;
  size: number;
  uploadedAt: string;
}

export interface UploadResponse {
  uploadId: string;
  fileName: string;
  contentType: string;
  size: number;
  objectKey: string;
  status: UploadStatus;
  partSize: number;
  totalParts: number;
  uploadedParts: number;
  parts: UploadPartResponse[];
  createdAt: string;
  updatedAt: string;
  expiresAt: string;
}

export interface UploadSummary {
  uploadId: string;
  fileName: string;
  contentType: string;
  size: number;
  objectKey: string;
  status: UploadStatus;
  totalParts: number;
  uploadedParts: number;
  downloadable: boolean;
  resumable: boolean;
  createdAt: string;
  updatedAt: string;
  expiresAt: string;
}

export interface UploadListResponse {
  items: UploadSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DownloadUrlResponse {
  uploadId: string;
  fileName: string;
  contentType: string;
  size: number;
  url: string;
  expiresAt: string;
}

export interface PresignedPart {
  partNumber: number;
  uploadUrl: string;
  expiresAt: string;
}

export interface PresignedPartsResponse {
  uploadId: string;
  parts: PresignedPart[];
}

export interface PartAckResponse {
  uploadId: string;
  partNumber: number;
  etag: string;
  uploadedParts: number;
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  code?: string;
  message?: string;
  path?: string;
  traceId?: string;
}

/** An API call that failed, carrying the uniform error body when present. */
export class ApiError extends Error {
  readonly httpStatus: number;
  readonly body: ApiErrorBody | null;

  constructor(httpStatus: number, body: ApiErrorBody | null, message: string) {
    super(message);
    this.name = 'ApiError';
    this.httpStatus = httpStatus;
    this.body = body;
  }

  get code(): string {
    return this.body?.code ?? `HTTP_${this.httpStatus}`;
  }
}
