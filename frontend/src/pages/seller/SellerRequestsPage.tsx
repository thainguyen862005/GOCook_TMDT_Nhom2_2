import { provinceList } from '../../constants/provinces';
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { workflowClient, type WorkflowRequest } from '../../clients/workflowClient';
import { SellerWorkspace } from '../../components/common/SellerWorkspace';
import { SellerPagination } from '../../components/common/SellerPagination';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { money } from '../../util/format';
import { filterChefRequests, type ChefRequestFilter } from '../../util/chefRequestFilter';

const PAGE_SIZE = 6;
type ChefRequest = WorkflowRequest & { myPrice?: number; myOfferStatus?: string };
export function SellerRequestsPage() {
  useDocumentTitle('Yêu cầu được ghép | GO Cook');
  const [rows, setRows] = useState<ChefRequest[]>([]);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<ChefRequestFilter>('all');
  const [area, setArea] = useState('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState<number | null>(null);
  const workingRef = useRef<number | null>(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let inFlight = false;
    const load = (showLoading: boolean) => {
      if (inFlight) return;
      inFlight = true;
      if (showLoading) setLoading(true);
      workflowClient.chefRequests(controller.signal)
        .then(results => {if (!controller.signal.aborted) {setRows(results);setError('');}})
        .catch(err => {if (!controller.signal.aborted) setError(err instanceof Error ? err.message : 'Không tải được yêu cầu.');})
        .finally(() => {inFlight = false; if (!controller.signal.aborted) setLoading(false);});
    };
    load(true);
    // Don't refresh while editing an offer: late responses must not overwrite the success state.
    const timer = window.setInterval(() => {if (document.visibilityState === 'visible' && workingRef.current === null) load(false);}, 15000);
    return () => {controller.abort(); window.clearInterval(timer);};
  }, [reload]);
  const areas = useMemo(() => [...new Set(rows.map(row => (row.serviceArea || '').split('|',1)[0]).filter(Boolean))].sort(), [rows]);
  const filtered = useMemo(() => filterChefRequests(rows, {query, status, area}), [rows, query, status, area]);
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const safePage = Math.min(page, Math.max(0, totalPages - 1));
  const visible = filtered.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);
  const pending = rows.filter(row => row.status !== 'CLOSED' && !row.myPrice).length;
  const reset = () => {setQuery('');setStatus('all');setArea('');setPage(0);};
  async function offer(event: FormEvent<HTMLFormElement>, id: number) {
    event.preventDefault();
    if (workingRef.current !== null) return;
    const form = new FormData(event.currentTarget);
    workingRef.current = id; setWorking(id); setError(''); setNotice('');
    try {
      await workflowClient.offer(id, {price: Number(form.get('price')), dishes: String(form.get('dishes')).trim(), note: String(form.get('note')).trim()});
      setNotice('Đã gửi báo giá. Danh sách đang được cập nhật.');
      setReload(value => value + 1);
    } catch (err) { setError(err instanceof Error ? err.message : 'Không thể gửi báo giá.'); }
    finally { workingRef.current = null; setWorking(null); }
  }
  return <SellerWorkspace title="Yêu cầu được ghép" description="Lọc yêu cầu phù hợp, trao đổi và gửi báo giá cho khách hàng.">
    <div className="chef-summary-strip" aria-label="Tổng quan yêu cầu">
      <div><span className="small muted">Tổng yêu cầu</span><strong>{rows.length}</strong></div>
      <div><span className="small muted">Cần phản hồi</span><strong>{pending}</strong></div>
      <div><span className="small muted">Đã báo giá</span><strong>{rows.filter(row => !!row.myPrice).length}</strong></div>
    </div>
    <section className="card stack-sm" aria-label="Lọc yêu cầu được ghép">
      <div className="chef-panel-head"><div><p className="eyebrow">Bộ lọc matching</p><h2>Tìm yêu cầu</h2></div>
        <div className="row"><button className="btn secondary sm" onClick={reset}>Xóa lọc</button><button className="btn secondary sm" onClick={() => setReload(n => n + 1)}>Làm mới</button></div>
      </div>
      <div className="chef-filters">
        <label className="field">Từ khóa<input type="search" maxLength={100} placeholder="Mã yêu cầu, khách, món ăn…" value={query} onChange={event => {setQuery(event.target.value);setPage(0);}} /></label>
        <label className="field">Trạng thái<select value={status} onChange={event => {setStatus(event.target.value as ChefRequestFilter);setPage(0);}}>
          <option value="all">Tất cả</option><option value="pending">Chưa báo giá</option><option value="quoted">Đã báo giá</option><option value="accepted">Báo giá được chọn</option><option value="closed">Đã đóng</option>
        </select></label>
        <label className="field">Khu vực<select value={area} onChange={event => {setArea(event.target.value);setPage(0);}}>
          <option value="">Tất cả khu vực</option>{areas.map(code => <option value={code} key={code}>{provinceList(code) || code}</option>)}
        </select></label>
      </div>
    </section>
    <div className="chef-panel-head"><div><h2>{filtered.length} yêu cầu phù hợp</h2><p className="small muted">Tự cập nhật mỗi 15 giây khi tab đang mở.</p></div>
      <Link className="btn secondary sm" to={ROUTES.MESSAGES}>Mở hộp thư</Link></div>
    {notice && <p role="status" className="notice">{notice}</p>}
    {error && <p className="notice warm" role="alert">{error}</p>}
    {loading ? <p role="status" className="card">Đang tải yêu cầu…</p> : !visible.length ? <div className="card chef-empty">
      <h3>{rows.length ? 'Không tìm thấy yêu cầu phù hợp' : 'Chưa có yêu cầu được ghép'}</h3>
      <p className="muted">{rows.length ? 'Thử đổi từ khóa hoặc bộ lọc.' : 'Kiểm tra trạng thái phê duyệt, khu vực phục vụ và matching của hồ sơ Chef.'}</p>
      {rows.length > 0 && <button className="btn secondary sm" onClick={reset}>Xóa bộ lọc</button>}
    </div> : <div className="chef-request-list">{visible.map(request => <article className="card chef-request-card" key={request.id}>
      <div className="chef-panel-head"><div><p className="eyebrow">{request.requestCode}</p><h3>{request.desiredDishes}</h3></div>
        <span className="badge gray">{request.status === 'CLOSED' ? (request.myOfferStatus === 'ACCEPTED' ? 'Được chọn' : 'Đã đóng') : request.myPrice ? 'Đã báo giá' : 'Chờ phản hồi'}</span></div>
      <div className="chef-request-details">
        <div><span className="small muted">Khách hàng</span><strong>{request.customerName || `#${request.customerId}`}</strong></div>
        <div><span className="small muted">Địa điểm</span><strong>{provinceList(request.serviceArea) || 'Chưa chỉ định'}</strong></div>
        <div><span className="small muted">Ngày nấu</span><strong>{request.mealDate} · {request.mealTime}</strong></div>
        <div><span className="small muted">Số khách</span><strong>{request.adults + request.children} người</strong></div>
      </div>
      <p className="small muted">{request.cuisine} · {request.address}</p>
      <div className="chef-price-row"><span>Ngân sách {money(Number(request.budgetMin))}–{money(Number(request.budgetMax))}</span>
        <strong>{request.myPrice ? `Đã báo ${money(Number(request.myPrice))}` : 'Chưa báo giá'}</strong></div>
      {request.myOfferStatus === 'ACCEPTED' && <div className="soft stack-sm">
        <strong>Khách đã chọn báo giá của bạn</strong><p>{request.myEscrowStatus === 'HELD' ? 'Đã tạm giữ tiền; có thể nhận đơn.' : 'Đang chờ khách thanh toán VNPay, chưa nhận nấu.'}</p>
        {request.myOrderId && <Link className="btn secondary sm" to={`/seller/order-detail?id=${request.myOrderId}`}>Xem đơn {request.myOrderCode}</Link>}
      </div>}
      {request.status !== 'CLOSED' && <form className="chef-offer-form" onSubmit={event => void offer(event, request.id)}>
        <label className="field">Giá đề xuất (VND)<input name="price" type="number" min="1" max="1000000000" defaultValue={request.myPrice || request.budgetMax} required /></label>
        <label className="field">Món ăn phục vụ<textarea name="dishes" rows={2} defaultValue={request.desiredDishes} maxLength={3000} required /></label>
        <label className="field">Ghi chú báo giá<textarea name="note" rows={2} maxLength={2000} placeholder="Điều kiện phục vụ hoặc ghi chú cho khách…" /></label>
        <button type="submit" className="btn" disabled={working !== null}>{working === request.id ? 'Đang gửi…' : request.myPrice ? 'Cập nhật báo giá' : 'Gửi báo giá'}</button>
      </form>}
      <Link className="btn secondary sm" to={`${ROUTES.CHAT}?requestId=${request.id}&peerId=${request.customerId}`}>Nhắn tin với khách →</Link>
    </article>)}</div>}
    <SellerPagination page={safePage} totalPages={totalPages} onPage={setPage} />
  </SellerWorkspace>;
}
