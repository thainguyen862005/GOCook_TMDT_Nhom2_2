import { useState } from 'react';
import { paymentError } from '../../clients/paymentClient';
import { beginOrderPayment } from './orderPayment';

/** Opens a new VNPay attempt for an order that is still waiting for payment. */
export function useRetryPayment(orderCode: string) {
  const [retrying, setRetrying] = useState(false);
  const [error, setError] = useState<string | null>(null);


  async function retry() {
    setRetrying(true);
    setError(null);
    try {
      await beginOrderPayment(orderCode);
    } catch (failure) {
      setError(paymentError(failure));
      setRetrying(false);
    }
  }

  return { retry, retrying, error };
}
