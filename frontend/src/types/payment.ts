export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'REFUNDED';

export type PaymentOrderStatus = 'PENDING' | 'CONFIRMED' | 'COOKING' | 'COMPLETED' | 'CANCELLED';

export interface CheckoutRequest {
  productId: number;
  guestCount: number;
  scheduledAt: string;
  customerName: string;
  customerPhone: string;
  serviceAddress: string;
  customerNote?: string;
  idempotencyKey: string;
  /** Optional; the server re-checks every rule and prices the order itself. */
  voucherCode?: string;
}

/** Pays an order that already exists as PENDING, e.g. the booking of an accepted proposal. */
export interface OrderCheckoutRequest {
  orderCode: string;
  idempotencyKey: string;
}

export interface CheckoutResult {
  orderCode: string;
  payUrl: string;
}

/** Why a payment attempt ended without success. */
export type PaymentCloseReason = 'USER_CANCELLED' | 'GATEWAY_FAILED' | 'EXPIRED';

export interface PaymentStatusResult {
  orderCode: string;
  paymentStatus: PaymentStatus;
  orderStatus: PaymentOrderStatus;
  productName: string;
  chefName: string | null;
  guestCount: number;
  scheduledAt: string;
  amount: number;
  paidAt: string | null;
  closeReason: PaymentCloseReason | null;
  attemptNo: number;
}
