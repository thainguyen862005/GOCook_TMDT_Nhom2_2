import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { notificationClient, internalNotificationPath } from '../../clients/notificationClient';
import type { NotificationPage } from '../../types/notification';

const empty: NotificationPage = { content: [], page: 0, size: 15, totalElements: 0 };
export function NotificationCenterPage() {
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [data, setData] = useState<NotificationPage>(empty);
  const [marketing, setMarketing] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const reload = useCallback(async () => {
    setLoading(true);
    try { setData(await notificationClient.page(page, 15, unreadOnly)); setError(''); }
    catch { setError('Không tải được thông báo. Vui lòng thử lại.'); }
    finally { setLoading(false); }
  }, [page, unreadOnly]);
  useEffect(() => { void reload(); }, [reload]);
  useEffect(() => { void notificationClient.preferences().then(p => setMarketing(p.marketingEnabled)).catch(() => {}); }, []);
  const mark = async (id: number) => {
    try { await notificationClient.read(id); await reload(); }
    catch { setError('Không cập nhật được trạng thái.'); }
  };
  return <div className="container notice-center">
    <div className="page-heading"><h1>Thông báo của tôi</h1><p className="lead">Cập nhật đơn hàng, yêu cầu hỗ trợ, tài khoản và tin tức từ GO Cook.</p></div>
    <div className="notice-controls">
      <label><input type="checkbox" checked={unreadOnly} onChange={e => { setPage(0); setUnreadOnly(e.target.checked); }} /> Chỉ chưa đọc</label>
      <button type="button" className="btn secondary sm" disabled={busy} onClick={async () => {
        setBusy(true); try { await notificationClient.readAll(); await reload(); }
        catch { setError('Không đánh dấu đã đọc được.'); } finally { setBusy(false); }
      }}>Đánh dấu tất cả đã đọc</button>
    </div>
    {error && <p role="alert" className="notice-error">{error} <button onClick={() => void reload()}>Thử lại</button></p>}
    {loading ? <p>Đang tải thông báo...</p> : <div className="notice-center-list">
      {!data.content.length && <p className="notice-empty">Không có thông báo phù hợp.</p>}
      {data.content.map(item => <article key={item.id} className={`notice-center-item ${item.read ? '' : 'is-unread'}`}>
        <div><small>{item.type} · {new Date(item.createdAt).toLocaleString('vi-VN')}</small>
        <h3>{item.title}</h3><p>{item.body}</p>
        <Link to={internalNotificationPath(item.actionPath)} onClick={() => { if (!item.read) void mark(item.id); }}>Xem chi tiết</Link></div>
        {!item.read && <button type="button" className="btn secondary sm" onClick={() => void mark(item.id)}>Đã đọc</button>}
      </article>)}
    </div>}
    <div className="notice-controls"><button type="button" disabled={page === 0} onClick={() => setPage(p => Math.max(0,p-1))}>Trang trước</button>
      <span>Trang {page+1} · {data.totalElements} thông báo</span>
      <button type="button" disabled={(page+1)*15 >= data.totalElements} onClick={() => setPage(p => p+1)}>Trang sau</button></div>
    <section className="card notice-preferences"><h2>Tùy chọn thông báo</h2>
      <label><input type="checkbox" checked={marketing} onChange={async e => {
        const next=e.target.checked;
        try { const result=await notificationClient.updatePreferences(next); setMarketing(result.marketingEnabled); }
        catch { setError('Không lưu được tùy chọn.'); }
      }} /> Nhận thông báo ưu đãi, khuyến mãi</label>
      <p>Thông báo quan trọng về giao dịch, bảo mật và trạng thái dịch vụ luôn được gửi.</p>
    </section>
  </div>;
}
