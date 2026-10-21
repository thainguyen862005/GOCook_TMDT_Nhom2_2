import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { adminClient } from '../../clients/adminClient';
import { Loading } from '../../components/common/Loading';
import { Notice } from '../../components/common/Notice';
import { Pager } from '../../components/common/Pager';
import { ORDER_LABEL } from '../../constants/labels';
import { ROUTES } from '../../constants/routes';
import { AdminLayout } from '../../layouts/AdminLayout';
import { useAdminPagedData } from '../../hooks/admin/useAdminPagedData';
import type { UiMessage } from '../../types/api';
import { dateTime, money } from '../../util/format';
import { statusClass } from '../../util/status';

function numeric(value: string | null, fallback: number) {
  if (!value || !/^\d+$/.test(value)) return fallback;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : fallback;
}

export function AdminOrdersPage() {
  const [params, setParams] = useSearchParams();
  const [notice, setNotice] = useState<UiMessage | null>(null);
  const q = params.get('q') || '';
  const status = params.get('status') || '';
  const from = params.get('from') || '';
  const to = params.get('to') || '';
  const page = numeric(params.get('page'), 0);
  const size = numeric(params.get('size'), 10);
  const key = JSON.stringify({ q, status, from, to, page, size });
  const { data, loading, error, refresh } = useAdminPagedData(
    () => adminClient.orders({ q, status, from: from || undefined, to: to || undefined, page, size }), key);

  useEffect(() => {
    if (data && page > 0 && (data.totalPages === 0 || page >= data.totalPages)) {
      const next = new URLSearchParams(params);
      next.set('page', String(Math.max(0, data.totalPages - 1)));
      setParams(next, { replace: true });
    }
  }, [data, page, params, setParams]);

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const start = String(form.get('from') || '');
    const end = String(form.get('to') || '');
    if (start && end && start > end) {
      setNotice({ text: 'Ngày bắt đầu không được sau ngày kết thúc.' });
      return;
    }
    setNotice(null);
    setParams({ q: String(form.get('q') || ''), status: String(form.get('status') || ''),
      from: start, to: end, size: String(form.get('size') || '10'), page: '0' });
  }

  return <AdminLayout title="Quản lý đơn hàng" lead="Theo dõi đơn và ghi chú xử lý, không can thiệp tiền/escrow.">
    <Notice message={notice} />
    <form key={key} className="card grid grid-3" onSubmit={apply}>
      <label className="field">Tìm đơn<input name="q" defaultValue={q} placeholder="Mã đơn, khách, menu, Chef" /></label>
      <label className="field">Trạng thái<select name="status" defaultValue={status}><option value="">Tất cả</option>
        {Object.keys(ORDER_LABEL).map((value) => <option key={value} value={value}>{ORDER_LABEL[value]}</option>)}</select></label>
      <label className="field">Từ ngày<input type="date" name="from" defaultValue={from} /></label>
      <label className="field">Đến ngày<input type="date" name="to" defaultValue={to} /></label>
      <label className="field">Số dòng<select name="size" defaultValue={size}>
        {[10,20,50].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
      <button className="btn">Lọc đơn hàng</button>
    </form>
    {loading ? <Loading /> : error ? <section className="card stack"><p role="alert">{error}</p>
      <button className="btn secondary" type="button" onClick={refresh}>Thử lại</button></section> : data && (
      <section className="card stack"><div className="table-wrap"><table>
        <caption>Hiển thị {data.numberOfElements} / {data.totalElements} đơn hàng</caption>
        <thead><tr><th>Mã</th><th>Khách hàng</th><th>Thực đơn</th><th>Ngày nấu</th><th>Tổng tiền</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
        <tbody>{data.content.length === 0 && <tr><td colSpan={7}>Không có đơn hàng phù hợp.</td></tr>}
          {data.content.map((order) => <tr key={order.id}>
            <td><strong>{order.orderCode}</strong></td><td>{order.customerName}</td><td>{order.productName}</td>
            <td>{order.scheduledAtDisplay || dateTime(order.scheduledAt)}</td><td>{money(order.totalAmount)}</td>
            <td><span className={`badge ${statusClass(order.status)}`}>{ORDER_LABEL[order.status] || order.status}</span></td>
            <td><Link className="text-link" to={`${ROUTES.ADMIN_ORDER_DETAIL}?id=${order.id}`}>Chi tiết</Link></td>
          </tr>)}</tbody></table></div>
        <Pager data={data} onPage={(next) => setParams({ q, status, from, to, size: String(size), page: String(next) })} />
      </section>
    )}
  </AdminLayout>;
}
