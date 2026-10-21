import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { publicContentClient } from '../../clients/publicContentClient';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { Offer } from '../../types/publicContent';
import { money } from '../../util/format';

export function OffersPage() {
  useDocumentTitle('Ưu đãi & khuyến mãi | GO Cook');
  const [offers, setOffers] = useState<Offer[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setError('');
    publicContentClient.offers(abort.signal).then(data => { if (!abort.signal.aborted) setOffers(data); })
      .catch(reason => { if (!abort.signal.aborted) setError(reason instanceof Error ? reason.message : 'Không thể tải ưu đãi.'); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [retry]);
  async function copy(code: string) {
    try { await navigator.clipboard.writeText(code); setCopied(code); }
    catch { setCopied(''); setError('Không sao chép được tự động. Bạn có thể chọn và chép mã trên thẻ ưu đãi.'); }
  }
  return <div className="container">
    <div className="page-heading"><nav className="breadcrumb"><Link to="/">Trang chủ</Link><span>/</span><span>Ưu đãi</span></nav>
      <h1>Ưu đãi dành cho bữa cơm nhà</h1><p className="lead">Danh sách được cập nhật từ chương trình khuyến mãi đang có hiệu lực.</p></div>
    <div className="dark-banner"><p className="eyebrow">GO Cook</p><h2>Thêm niềm vui cho bữa cơm nhà</h2>
      <p>Chỉ những mã đang được kích hoạt, còn thời hạn và lượt sử dụng mới xuất hiện.</p></div>
    {loading ? <p role="status" className="section">Đang tải ưu đãi…</p> : error ? <div className="card stack"><p role="alert">{error}</p><button className="btn secondary" onClick={() => setRetry(x => x + 1)}>Thử lại</button></div>
      : offers.length === 0 ? <div className="card stack section"><h2>Chưa có ưu đãi đang áp dụng</h2><p>Hãy quay lại sau hoặc khám phá những thực đơn hiện có.</p><Link className="btn" to="/menus">Xem thực đơn</Link></div>
        : <section className="section grid grid-3" aria-live="polite">{offers.map(offer => <article key={offer.code} className="voucher">
            <span className="badge">Đang có hiệu lực</span>
            <strong>{offer.discountType === 'PERCENT' ? `Giảm ${Number(offer.discountValue)}%` : `Giảm ${money(Number(offer.discountValue))}`}</strong>
            <h3>{offer.description || 'Ưu đãi GO Cook'}</h3>
            <p className="small muted">Áp dụng cho đơn từ {money(Number(offer.minOrderAmount))}{offer.maxDiscount ? ` · Giảm tối đa ${money(Number(offer.maxDiscount))}` : ''}</p>
            <p className="small muted">Hết hạn: {new Date(offer.endsAt).toLocaleDateString('vi-VN')}</p>
            <span className="code">{offer.code}</span>
            <button type="button" className="btn secondary" onClick={() => copy(offer.code)}>{copied === offer.code ? 'Đã sao chép' : 'Sao chép mã'}</button>
          </article>)}</section>}
    <p className="notice">Mã khuyến mãi được công bố tại đây; việc áp dụng thực tế lúc thanh toán phụ thuộc vào tích hợp của module Voucher/Checkout.</p>
  </div>;
}
