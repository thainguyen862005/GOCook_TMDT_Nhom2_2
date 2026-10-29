import { ApiError, httpRequest } from './httpClient';
import type {
  CheckoutRequest,
  CheckoutResult,
  OrderCheckoutRequest,
  PaymentStatusResult,
} from '../types/payment';

export const paymentClient = {
  checkout: (request: CheckoutRequest) =>
    httpRequest<CheckoutResult>('/api/payments/checkout', {
      method: 'POST',
      body: JSON.stringify(request),
    }),

  checkoutOrder: (request: OrderCheckoutRequest) =>
    httpRequest<CheckoutResult>('/api/payments/checkout/order', {
      method: 'POST',
      body: JSON.stringify(request),
    }),

  /** Sends VNPay signed callback parameters for verified, idempotent settlement and escrow registration. */
  vnpayReturn: (params: Record<string, string>) =>
    httpRequest<PaymentStatusResult>('/api/payments/vnpay-return', {
      method: 'POST',
      body: JSON.stringify(params),
    }),

  status: (txnRef: string, signal?: AbortSignal) =>
    httpRequest<PaymentStatusResult>(`/api/payments/status?${new URLSearchParams({ txnRef })}`, {
      signal,
    }),
};

export function paymentError(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return 'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.';
  }
  if (error instanceof ApiError && error.status === 403) {
    return 'Tài khoản này không có quyền đặt lịch và thanh toán.';
  }
  return error instanceof Error ? error.message : 'Không thể kết nối hệ thống. Vui lòng thử lại.';
}
