import { useEffect, useRef, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { menuClient } from '../../clients/menuClient';
import { paymentClient, paymentError } from '../../clients/paymentClient';
import { pricingClient } from '../../clients/pricingClient';
import type { UiMessage } from '../../types/api';
import type { MenuDetail } from '../../types/menu';
import type { FixedMenuPreview } from '../../types/pricing';
import { useAuth } from '../auth/useAuth';
import { rememberCheckout } from './checkoutRetry';
import { beginOrderPayment, redirectToVnpay } from './orderPayment';

const BASE_TIMES = ['11:30', '18:30'];

export function useCheckout() {
  const [params] = useSearchParams();
  const location = useLocation();
  const { profile } = useAuth();

  const productId = Number(params.get('productId'));
  const hasProductId = Number.isInteger(productId) && productId > 0;

  const [product, setProduct] = useState<MenuDetail | null>(null);
  const [createdOrderCode, setCreatedOrderCode] = useState<string | null>(null);
  const [loading, setLoading] = useState(hasProductId);
  const [message, setMessage] = useState<UiMessage | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const idempotencyKey = useRef<string | null>(null);
  const pendingOrderCode = useRef<string | null>(null);

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
  const [voucherCode, setVoucherCode] = useState('');
  const [voucher, setVoucher] = useState<FixedMenuPreview | null>(null);
  const [applyingVoucher, setApplyingVoucher] = useState(false);

  useEffect(() => {
    // Profile is loaded asynchronously. Do not overwrite any information the customer has typed.
    if (profile?.username) setCustomerName(current => current || profile.username);
    if (profile?.phone) setCustomerPhone(current => current || profile.phone || '');
    if (profile?.defaultAddress) setServiceAddress(current => current || profile.defaultAddress || '');
  }, [profile?.username, profile?.phone, profile?.defaultAddress]);

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
  const voucherApplies = voucher !== null && voucher.guestCount === guestCount;
  const discount = voucherApplies ? voucher.discount : 0;
  const total = voucherApplies
    ? voucher.total
    : product
      ? Number(product.unitPrice) * guestCount
      : 0;
  const times = Array.from(new Set([...BASE_TIMES, time]));
  const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  const scheduledAt = date && time ? new Date(`${date}T${time}:00`) : null;
  const futureSchedule = Boolean(scheduledAt && Number.isFinite(scheduledAt.getTime()) && scheduledAt.getTime() > Date.now());

  const canSubmit =
    product !== null &&
    guestsInRange &&
    agreed &&
    !submitting &&
    !applyingVoucher &&
    customerName.trim() !== '' &&
    customerPhone.trim() !== '' &&
    serviceAddress.trim() !== '' &&
    date !== '' &&
    futureSchedule;

  async function applyVoucher() {
    if (!product || !guestsInRange || voucherCode.trim() === '') return;
    setApplyingVoucher(true);
    setMessage(null);
    try {
      setVoucher(
        await pricingClient.fixedPreview({
          productId: product.id,
          guestCount,
          voucherCode: voucherCode.trim(),
        }),
      );
    } catch (error) {
      setVoucher(null);
      setMessage({ type: 'error', text: paymentError(error) });
    } finally {
      setApplyingVoucher(false);
    }
  }

  function clearVoucher() {
    setVoucher(null);
    setVoucherCode('');
  }

  async function submit() {
    if (submitting || !product) return;
    if (!canSubmit) {
      const missing = [
        !guestsInRange && 'số khách hợp lệ',
        !customerName.trim() && 'họ tên',
        !customerPhone.trim() && 'số điện thoại',
        !serviceAddress.trim() && 'địa chỉ',
        !futureSchedule && 'ngày/giờ tương lai',
        !agreed && 'đồng ý chính sách',
      ].filter(Boolean).join(', ');
      setMessage({type:'error', text:`Hãy hoàn tất: ${missing || 'thông tin đặt lịch'}.`});
      return;
    }
    setSubmitting(true);
    setMessage(null);
    idempotencyKey.current ??= crypto.randomUUID();
    try {
      // A valid previously created order must be reused if gateway navigation failed.
      if (pendingOrderCode.current) {
        await beginOrderPayment(pendingOrderCode.current);
        return;
      }
      const result = await paymentClient.checkout({
        productId: product.id,
        guestCount,
        scheduledAt: `${date}T${time}:00`,
        customerName: customerName.trim(),
        customerPhone: customerPhone.trim(),
        serviceAddress: serviceAddress.trim(),
        customerNote: customerNote.trim() || undefined,
        idempotencyKey: idempotencyKey.current,
        voucherCode: voucherApplies ? voucher.voucherCode ?? undefined : undefined,
      });
      pendingOrderCode.current = result.orderCode;
      setCreatedOrderCode(result.orderCode);
      rememberCheckout(result.orderCode, { path: location.pathname, search: location.search });
      redirectToVnpay(result, result.orderCode);
    } catch (error) {
      // Keep idempotency key across failures. Retrying must not charge/create another order.
      // A transient network failure replays the same request through the backend.
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
    createdOrderCode,
    today,
    futureSchedule,
    canSubmit,
    submit,
    guestCount,
    guestsInRange,
    total,
    discount,
    voucher: {
      code: voucherCode,
      applied: voucherApplies ? voucher : null,
      applying: applyingVoucher,
      setCode: setVoucherCode,
      apply: applyVoucher,
      clear: clearVoucher,
    },
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
