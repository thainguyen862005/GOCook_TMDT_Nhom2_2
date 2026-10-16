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
}

export interface CheckoutResult {
  orderCode: string;
  payUrl: string;
}

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
}
