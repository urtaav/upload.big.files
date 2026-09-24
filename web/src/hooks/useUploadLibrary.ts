import { useCallback, useEffect, useState } from 'react';
import type { UploadApi } from '../lib/api';
import type { UploadListResponse, UploadStatus } from '../lib/types';

export type LibraryFilter = 'todos' | 'listos' | 'incompletos';

const FILTER_STATUSES: Record<LibraryFilter, UploadStatus[] | undefined> = {
  todos: undefined,
  listos: ['COMPLETED', 'PROCESSING', 'READY'],
  incompletos: ['CREATED', 'UPLOADING'],
};

const PAGE_SIZE = 8;

/**
 * Reads the file explorer straight from the API. Nothing is cached in the
 * browser on purpose: the listing is meant to show what the server actually
 * holds, including sessions started from another tab or another machine.
 */
export function useUploadLibrary(api: UploadApi) {
  const [data, setData] = useState<UploadListResponse | null>(null);
  const [filter, setFilter] = useState<LibraryFilter>('todos');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await api.listUploads({ statuses: FILTER_STATUSES[filter], page, size: PAGE_SIZE }));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setLoading(false);
    }
  }, [api, filter, page]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  /**
   * Fetches a signed URL and hands it to the browser. The click that starts the
   * download is a plain navigation to storage: the bytes never pass through the
   * API, exactly as on the way up.
   */
  const download = useCallback(
    async (uploadId: string) => {
      setError(null);
      try {
        const link = await api.downloadUrl(uploadId);
        const anchor = document.createElement('a');
        anchor.href = link.url;
        anchor.rel = 'noopener';
        // The name is already pinned into the signature; this only helps when
        // the browser opens the URL in a context that ignores the header.
        anchor.download = link.fileName;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : String(cause));
      }
    },
    [api],
  );

  const changeFilter = useCallback((next: LibraryFilter) => {
    setFilter(next);
    setPage(0);
  }, []);

  return {
    data,
    filter,
    page,
    loading,
    error,
    pageSize: PAGE_SIZE,
    setPage,
    changeFilter,
    refresh,
    download,
  };
}
