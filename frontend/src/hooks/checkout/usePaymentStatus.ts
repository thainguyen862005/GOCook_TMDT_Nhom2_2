import { useCallback, useEffect, useRef, useState } from 'react';
import { paymentClient, paymentError } from '../../clients/paymentClient';
import type { PaymentStatusResult } from '../../types/payment';

const POLL_INTERVAL_MS = 3000;
const MAX_POLLS = 10;

export type PaymentView = 'missing' | 'confirming' | 'waiting' | 'success' | 'failed' | 'error';

/**
 * returnParams are the vnp_* query parameters of the return URL (null when there is none). When
 * present they are sent to the backend once per transaction before polling starts.
 */
export function usePaymentStatus(
  txnRef: string | null,
  returnParams: Record<string, string> | null,
) {
  const [result, setResult] = useState<PaymentStatusResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [waiting, setWaiting] = useState(false);
  const [round, setRound] = useState(0);
  const returnRef = useRef(returnParams);
  returnRef.current = returnParams;
  const returnSent = useRef<string | null>(null);

  useEffect(() => {
    if (!txnRef) return;
    let cancelled = false;
    const controller = new AbortController();

    async function poll(reference: string) {
      if (returnRef.current && returnSent.current !== reference) {
        returnSent.current = reference;
        try {
          await paymentClient.vnpayReturn(returnRef.current);
        } catch (failure) {
          if (!cancelled) setError(paymentError(failure));
          return;
        }
      }
      for (let attempt = 0; attempt < MAX_POLLS && !cancelled; attempt += 1) {
        try {
          const latest = await paymentClient.status(reference, controller.signal);
          if (cancelled) return;
          setResult(latest);
          if (latest.paymentStatus === 'FAILED' || latest.paymentStatus === 'CANCELLED' || latest.paymentStatus === 'REFUNDED'
            || (latest.paymentStatus === 'SUCCESS' && latest.escrowConfirmed)) return;
        } catch (failure) {
          if (!cancelled) setError(paymentError(failure));
          return;
        }
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
      }
      if (!cancelled) setWaiting(true);
    }

    setResult(null);
    setError(null);
    setWaiting(false);
    void poll(txnRef);

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [txnRef, round]);

  const recheck = useCallback(() => setRound((value) => value + 1), []);

  let view: PaymentView;
  if (!txnRef) view = 'missing';
  else if (error) view = 'error';
  else if (!result) view = 'confirming';
  else if (result.paymentStatus === 'SUCCESS') view = result.escrowConfirmed ? 'success' : 'waiting';
  else if (result.paymentStatus === 'PENDING') view = waiting ? 'waiting' : 'confirming';
  else view = 'failed';

  return { view, result, error, recheck };
}
