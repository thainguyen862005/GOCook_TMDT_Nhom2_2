import { httpRequest } from './httpClient';
import type {
  FinanceRangeQuery,
  FinanceStatus,
  FinanceSummary,
  FinanceTransactionPage,
  MonthlyIncome,
} from '../types/sellerFinance';

function query(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([name, value]) => {
    if (value !== undefined && value !== '') params.set(name, String(value));
  });
  const text = params.toString();
  return text ? `?${text}` : '';
}

export const sellerFinanceClient = {
  summary: (range: FinanceRangeQuery, signal?: AbortSignal) =>
    httpRequest<FinanceSummary>(`/api/seller/finance/summary${query({ ...range })}`, { signal }),

  monthly: (months: number, signal?: AbortSignal) =>
    httpRequest<MonthlyIncome[]>(`/api/seller/finance/monthly${query({ months })}`, { signal }),

  transactions: (
    range: FinanceRangeQuery & { page: number; size: number; status?: FinanceStatus },
    signal?: AbortSignal,
  ) =>
    httpRequest<FinanceTransactionPage>(`/api/seller/finance/transactions${query({ ...range })}`, {
      signal,
    }),
};
