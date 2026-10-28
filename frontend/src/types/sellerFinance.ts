/** Money in VND. Times are ISO-8601 text with the +07:00 offset (Asia/Ho_Chi_Minh). */

export interface FinanceSummary {
  /** Chef share of orders the customer confirmed (RELEASED). */
  availableAmount: number;
  /** Chef share still held for orders not yet confirmed (HELD). */
  pendingAmount: number;
  /** Platform fee of the released orders. */
  platformFeeTotal: number;
  releasedOrderCount: number;
  /** Paid orders (held or released) by kind, for the pie chart. */
  menuOrderCount: number;
  budgetOrderCount: number;
}

export interface MonthlyIncome {
  /** yyyy-MM, local month of released_at. */
  month: string;
  earnedAmount: number;
  orderCount: number;
}

export type FinanceStatus = 'HELD' | 'RELEASED';

export interface FinanceTransaction {
  orderCode: string;
  grossAmount: number;
  platformFee: number;
  /** fee / gross of this row, two decimals. Never assume a fixed percentage. */
  platformFeePercent: number;
  chefAmount: number;
  status: FinanceStatus;
  heldAt: string;
  releasedAt: string | null;
}

export interface FinanceTransactionPage {
  content: FinanceTransaction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Local days (yyyy-MM-dd), both included. Leave a side out for no limit. */
export interface FinanceRangeQuery {
  from?: string;
  to?: string;
}
