import { paymentClient } from '../../clients/paymentClient';
import type { CheckoutResult } from '../../types/payment';

/**
 * Checkout for an already booked order (accepted Chef quote or unpaid menu booking).
 * A re-click reuses the same pending gateway attempt, but only Payment/IPN can mark it paid.
 */
export async function beginOrderPayment(orderCode: string): Promise<void> {
  if (!orderCode || !/^[A-Za-z0-9-]{6,50}$/.test(orderCode)) {
    throw new Error('Mã đơn cần thanh toán không hợp lệ.');
  }
  const result = await paymentClient.checkoutOrder({
    orderCode,
    idempotencyKey: crypto.randomUUID(),
  });
  redirectToVnpay(result, orderCode);
}

/** Shared by fixed-menu checkout and Chef quote checkout, with strict gateway validation. */
export function redirectToVnpay(result: CheckoutResult, orderCode: string): void {
  if (result.orderCode !== orderCode || !result.payUrl) {
    throw new Error('Cổng thanh toán không trả về đúng đơn hàng.');
  }
  let url: URL;
  try { url = new URL(result.payUrl); }
  catch { throw new Error('Địa chỉ cổng VNPay không hợp lệ.'); }
  // Never let a compromised checkout response redirect to an untrusted site.
  const validHost = url.hostname === 'vnpayment.vn' || url.hostname.endsWith('.vnpayment.vn');
  if (url.protocol !== 'https:' || !validHost || !url.searchParams.has('vnp_SecureHash')) {
    throw new Error('Cấu hình URL VNPay không hợp lệ; kiểm tra VNPAY_PAY_URL của backend.');
  }
  window.location.assign(url.toString());
}
