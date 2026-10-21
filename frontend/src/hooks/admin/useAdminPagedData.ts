import { useEffect, useState } from 'react';

export function useAdminPagedData<T>(loader: () => Promise<T>, key: string) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    setData(null);
    loader().then((result) => {
      if (active) setData(result);
    }).catch((failure: unknown) => {
      if (active) setError(failure instanceof Error ? failure.message : 'Không thể tải dữ liệu.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
    // The key captures all query parameters; loader belongs to the render of that key.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, retry]);

  return { data, loading, error, refresh: () => setRetry((value) => value + 1) };
}
