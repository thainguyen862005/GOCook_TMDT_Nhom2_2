import { useRef, useState } from 'react';
import { paymentClient, paymentError } from '../../clients/paymentClient';

/** Opens a new VNPay attempt for an order that is still waiting for payment. */
export function useRetryPayment(orderCode: string) {
  const [retrying, setRetrying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const idempotencyKey = useRef<string | null>(null);

  async function retry() {
    setRetrying(true);
    setError(null);
    idempotencyKey.current ??= crypto.randomUUID();
    try {
      const result = await paymentClient.checkoutOrder({
        orderCode,
        idempotencyKey: idempotencyKey.current,
      });
      window.location.assign(result.payUrl);
    } catch (failure) {
      idempotencyKey.current = null;
      setError(paymentError(failure));
      setRetrying(false);
    }
  }

  return { retry, retrying, error };
}
