import { useEffect, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { menuClient } from '../../clients/menuClient';
import { paymentClient, paymentError } from '../../clients/paymentClient';
import type { UiMessage } from '../../types/api';
import type { MenuDetail } from '../../types/menu';
import { useAuth } from '../auth/useAuth';
import { rememberCheckout } from './checkoutRetry';

const BASE_TIMES = ['11:30', '18:30'];

export function useCheckout() {
  const [params] = useSearchParams();
  const location = useLocation();
  const { profile } = useAuth();

  const productId = Number(params.get('productId'));
  const hasProductId = Number.isInteger(productId) && productId > 0;

  const [product, setProduct] = useState<MenuDetail | null>(null);
  const [loading, setLoading] = useState(hasProductId);
  const [message, setMessage] = useState<UiMessage | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [guests, setGuests] = useState<number | null>(() => {
    const requested = Number(params.get('guests'));
    return Number.isInteger(requested) && requested > 0 ? requested : null;
  });
  const [customerName, setCustomerName] = useState(profile?.username ?? '');
  const [customerPhone, setCustomerPhone] = useState('');
  const [serviceAddress, setServiceAddress] = useState('');
  const [date, setDate] = useState(params.get('date') ?? '');
  const [time, setTime] = useState(params.get('time') ?? BASE_TIMES[1]);
  const [customerNote, setCustomerNote] = useState('');
  const [agreed, setAgreed] = useState(false);

  useEffect(() => {
    if (!hasProductId) return;
    const controller = new AbortController();
    setLoading(true);
    menuClient
      .get(String(productId), controller.signal)
      .then(setProduct)
      .catch((error) => {
        if (!controller.signal.aborted) setMessage({ type: 'error', text: paymentError(error) });
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [hasProductId, productId]);

  const guestCount = guests ?? product?.minGuests ?? 0;
  const guestsInRange =
    product !== null && guestCount >= product.minGuests && guestCount <= product.maxGuests;
  const total = product ? Number(product.unitPrice) * guestCount : 0;
  const times = Array.from(new Set([...BASE_TIMES, time]));

  const canSubmit =
    product !== null &&
    guestsInRange &&
    agreed &&
    !submitting &&
    customerName.trim() !== '' &&
    customerPhone.trim() !== '' &&
    serviceAddress.trim() !== '' &&
    date !== '';

  async function submit() {
    if (!product || !canSubmit) return;
    setSubmitting(true);
    setMessage(null);
    try {
      const result = await paymentClient.checkout({
        productId: product.id,
        guestCount,
        scheduledAt: `${date}T${time}:00`,
        customerName: customerName.trim(),
        customerPhone: customerPhone.trim(),
        serviceAddress: serviceAddress.trim(),
        customerNote: customerNote.trim() || undefined,
      });
      rememberCheckout(result.orderCode, { path: location.pathname, search: location.search });
      window.location.assign(result.payUrl);
    } catch (error) {
      setMessage({ type: 'error', text: paymentError(error) });
      setSubmitting(false);
    }
  }

  return {
    hasProductId,
    product,
    loading,
    message,
    submitting,
    canSubmit,
    submit,
    guestCount,
    guestsInRange,
    total,
    times,
    form: {
      customerName,
      customerPhone,
      serviceAddress,
      date,
      time,
      customerNote,
      agreed,
      setGuests,
      setCustomerName,
      setCustomerPhone,
      setServiceAddress,
      setDate,
      setTime,
      setCustomerNote,
      setAgreed,
    },
  };
}
