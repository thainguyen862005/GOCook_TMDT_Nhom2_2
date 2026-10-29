
import type { PageResponse } from './api';

export type CustomerOrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PREPARING'
  | 'COOKING'
  | 'AWAITING_ACCEPTANCE'
  | 'REJECTED'
  | 'COMPLETED'
  | 'CANCELLED';

export interface CustomerOrder {
  id: number;
  orderCode: string;
  productName: string;
  chefName: string | null;
  guestCount: number;
  createdAt: string | null;
  scheduledAt: string | null;
  totalAmount: number | string;
  status: CustomerOrderStatus;
  serviceAddress?: string | null;
  customerNote?: string | null;
  escrowStatus?: 'HELD' | 'RELEASED' | 'REFUND_PENDING' | 'REFUNDED' | null;
}

export type CustomerOrdersResponse = PageResponse<CustomerOrder>;
