import { useEffect, useState } from 'react';
import { ApiError } from '../../clients/httpClient';
import { sellerFinanceClient } from '../../clients/sellerFinanceClient';
import type {
  FinanceSummary,
  FinanceTransactionPage,
  MonthlyIncome,
} from '../../types/sellerFinance';

const MONTHS = 6;
const PAGE_SIZE = 10;

function financeError(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return 'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.';
  }
  if (error instanceof ApiError && error.status === 403) {
    return 'Chỉ tài khoản đầu bếp mới xem được doanh thu.';
  }
  return error instanceof Error ? error.message : 'Không thể kết nối hệ thống. Vui lòng thử lại.';
}

interface Loaded<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
}

function useLoaded<T>(load: (signal: AbortSignal) => Promise<T>, deps: unknown[]): Loaded<T> {
  const [state, setState] = useState<Loaded<T>>({ data: null, loading: true, error: null });
  useEffect(() => {
    const controller = new AbortController();
    setState((previous) => ({ data: previous.data, loading: true, error: null }));
    load(controller.signal)
      .then((data) => setState({ data, loading: false, error: null }))
      .catch((error) => {
        if (!controller.signal.aborted) {
          setState({ data: null, loading: false, error: financeError(error) });
        }
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return state;
}

/** Earnings of the logged-in chef for a range of local days; the list is paged. */
export function useSellerFinance(from: string, to: string) {
  const [page, setPage] = useState(0);
  const range = { from: from || undefined, to: to || undefined };

  const summary = useLoaded<FinanceSummary>(
    (signal) => sellerFinanceClient.summary(range, signal),
    [from, to],
  );
  const monthly = useLoaded<MonthlyIncome[]>(
    (signal) => sellerFinanceClient.monthly(MONTHS, signal),
    [],
  );
  const transactions = useLoaded<FinanceTransactionPage>(
    (signal) => sellerFinanceClient.transactions({ ...range, page, size: PAGE_SIZE }, signal),
    [from, to, page],
  );

  return {
    summary,
    monthly,
    transactions,
    page,
    goToPage: setPage,
    resetPage: () => setPage(0),
  };
}
