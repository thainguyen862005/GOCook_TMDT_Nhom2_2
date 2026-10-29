import type { PageResponse } from './api';
import type { PaymentStatus } from './payment';

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

  serviceType: 'MENU' | 'BUDGET' | null;

  productName: string | null;
  chefName: string | null;

  guestCount: number;

  createdAt: number | null;
  scheduledAt: string | null;

  totalAmount: number | string;

  status: CustomerOrderStatus;

  serviceAddress: string | null;
  customerNote: string | null;

  paymentStatus: PaymentStatus | null;
  paidAt: string | null;

  refundedAmount: number | string;
}

export type CustomerOrdersResponse =
  PageResponse<CustomerOrder>;