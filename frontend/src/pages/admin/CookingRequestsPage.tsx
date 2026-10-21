import { useEffect, useState } from 'react';
import { AdminLayout } from '../../layouts/AdminLayout';
import { publicContentClient } from '../../clients/publicContentClient';

export function AdminCookingRequestsPage() {
  const [rows, setRows] = useState<Array<Record<string, unknown>>>([]);
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true); setError('');
    publicContentClient.adminRequests(page).then(setRows)
      .catch(reason => setError(reason instanceof Error ? reason.message : 'Không tải được yêu cầu.'))
      .finally(() => setLoading(false));
  }, [page]);
  return <AdminLayout title="Yêu cầu nấu ăn riêng" lead="Hộp tiếp nhận yêu cầu từ khách hàng. Đây chưa phải Booking hoặc Payment; module liên quan có thể tích hợp tiếp.">
    {loading ? <p role="status">Đang tải…</p> : error ? <p role="alert" className="notice warm">{error}</p>
      : rows.length === 0 ? <p>Chưa có yêu cầu ở trang này.</p> : rows.map(row => <article key={String(row.id)} className="card stack">
        <div className="between"><h3>{String(row.requestCode)}</h3><span className="badge">{String(row.status)}</span></div>
        <p>Khách hàng: {String(row.customerName)} · Ngày: {String(row.mealDate)} ({String(row.mealTime)})</p>
        <p>{String(row.desiredDishes)}</p><p className="small muted">{String(row.cuisine)} · {String(row.adults)} người lớn, {String(row.children)} trẻ em</p>
        <p className="small muted">Địa chỉ: {String(row.address)}</p>
        <p className="small muted">Dị ứng / kiêng: {String(row.avoid || 'Không có')}</p>
        <p className="small muted">Ngân sách: {String(row.budgetMin)} – {String(row.budgetMax)} đ</p>
        {typeof row.referenceImageUrl === 'string' && row.referenceImageUrl && <img className="admin-media-preview" src={row.referenceImageUrl} alt="Ảnh tham khảo của yêu cầu" />}
      </article>)}
    <div className="between"><button className="btn secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>← Trước</button>
      <span>Trang {page + 1}</span><button className="btn secondary" disabled={rows.length < 30} onClick={() => setPage(p => p + 1)}>Sau →</button></div>
  </AdminLayout>;
}
