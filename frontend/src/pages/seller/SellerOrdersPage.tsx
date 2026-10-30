import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { chefOrderClient } from '../../clients/chefOrderClient';
import { SellerWorkspace } from '../../components/common/SellerWorkspace';
import { SellerPagination } from '../../components/common/SellerPagination';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { ChefOrderPage } from '../../types/chefOrder';
import { chefStatusLabels } from '../../types/chefOrder';
import { dateTime, money } from '../../util/format';

const PAGE_SIZE = 10;
function paymentLabel(escrow: string | null) {
  switch (escrow) {
    case 'HELD': return 'Đang tạm giữ';
    case 'RELEASED': return 'Đã giải ngân';
    case 'REFUND_PENDING': return 'Chờ hoàn tiền';
    case 'REFUNDED': return 'Đã hoàn tiền';
    default: return 'Chưa ghi nhận thanh toán';
  }
}

export function SellerOrdersPage() {
  useDocumentTitle('Đơn đặt nấu | GO Cook');
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [serviceType, setServiceType] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<ChefOrderPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const invalidDates = Boolean(from && to && from > to);
  useEffect(() => {
    const timeout = window.setTimeout(() => setSearch(keyword.trim()), 300);
    return () => window.clearTimeout(timeout);
  }, [keyword]);
  useEffect(() => {
    if (invalidDates) { setData(null); setLoading(false); return; }
    const controller = new AbortController();
    setLoading(true); setError(''); setData(null);
    chefOrderClient.list({ q: search, status, serviceType, from, to, page, size: PAGE_SIZE }, controller.signal)
      .then(result => {
        if (controller.signal.aborted) return;
        if (page > 0 && page >= result.totalPages) { setPage(Math.max(0, result.totalPages - 1)); return; }
        setData(result);
      })
      .catch(err => {
        if (!controller.signal.aborted) setError(err instanceof Error ? err.message : 'Không tải được danh sách đơn.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [search, status, serviceType, from, to, page, reload, invalidDates]);
  const reset = () => { setKeyword(''); setSearch(''); setStatus(''); setServiceType(''); setFrom(''); setTo(''); setPage(0); };
  return <SellerWorkspace title="Đơn đặt nấu" description="Tìm và quản lý đơn của bạn, từ chờ thanh toán đến nghiệm thu.">
    <section className="card stack-sm" aria-label="Tìm kiếm và lọc đơn đặt nấu">
      <div className="chef-panel-head"><div><p className="eyebrow">Bộ lọc đơn hàng</p><h2>Tra cứu nhanh</h2></div>
        <button className="btn secondary sm" type="button" onClick={reset}>Xóa bộ lọc</button></div>
      <div className="chef-filters">
        <label className="field">Tìm đơn hàng<input type="search" maxLength={100} placeholder="Mã đơn, tên khách, tên thực đơn…"
          value={keyword} onChange={event => {setKeyword(event.target.value);setPage(0);}} /></label>
        <label className="field">Trạng thái đơn<select value={status} onChange={event => {setStatus(event.target.value);setPage(0);}}>
          <option value="">Tất cả trạng thái</option>
          {Object.entries(chefStatusLabels).map(([value,label]) => <option value={value} key={value}>{label}</option>)}
        </select></label>
        <label className="field">Loại dịch vụ<select value={serviceType} onChange={event => {setServiceType(event.target.value);setPage(0);}}>
          <option value="">Tất cả dịch vụ</option><option value="MENU">Thực đơn có sẵn</option><option value="BUDGET">Theo ngân sách</option>
        </select></label>
        <label className="field">Từ ngày<input type="date" value={from} onChange={event => {setFrom(event.target.value);setPage(0);}} /></label>
        <label className="field">Đến ngày<input type="date" value={to} onChange={event => {setTo(event.target.value);setPage(0);}} /></label>
      </div>
      {invalidDates && <p role="alert" className="notice warm">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}
    </section>
    <section className="card stack" aria-live="polite">
      <div className="chef-panel-head"><div><p className="eyebrow">Danh sách đơn thực tế</p><h2>{loading ? 'Đang tìm đơn…' : `${data?.totalElements ?? 0} đơn phù hợp`}</h2></div>
        <button className="btn secondary sm" type="button" onClick={() => setReload(n => n + 1)}>Làm mới</button></div>
      <p className="small muted">Chỉ hiển thị đơn thuộc tài khoản Chef hiện tại. Đơn chưa thanh toán không thể nhận nấu.</p>
      {loading ? <p role="status">Đang tải đơn đặt nấu…</p> : error ? <div className="notice warm" role="alert">{error}</div> : <>
        {!!data?.content.length ? <div className="table-wrap"><table>
          <thead><tr><th>Mã đơn / Khách</th><th>Thực đơn</th><th>Thời gian</th><th>Giá trị</th><th>Thanh toán</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
          <tbody>{data.content.map(order => <tr key={order.id}>
            <td><strong>{order.orderCode}</strong><div className="small muted">{order.customerName}</div></td>
            <td>{order.productName}<div className="small muted">{order.guestCount} khách · {order.serviceType === 'MENU' ? 'Thực đơn' : 'Theo ngân sách'}</div></td>
            <td>{dateTime(order.scheduledAt)}</td><td>{money(order.totalAmount)}</td>
            <td><span className="chef-payment-label">{paymentLabel(order.escrowStatus)}</span></td>
            <td><span className="badge gray">{order.status === 'PENDING' && order.escrowStatus !== 'HELD' ? 'Chờ khách thanh toán' : chefStatusLabels[order.status] || order.status}</span></td>
            <td><Link className="btn secondary sm" to={`/seller/order-detail?id=${order.id}`}>Chi tiết</Link></td>
          </tr>)}</tbody></table></div> : <div className="chef-empty soft"><h3>Không tìm thấy đơn phù hợp</h3><p className="small muted">Thử mã đơn khác, đổi ngày hoặc bỏ bớt bộ lọc.</p><button className="btn secondary sm" onClick={reset}>Xóa bộ lọc</button></div>}
        <SellerPagination page={page} totalPages={data?.totalPages ?? 0} onPage={setPage}/>
      </>}
    </section>
  </SellerWorkspace>;
}
