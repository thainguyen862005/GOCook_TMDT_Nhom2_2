import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { adminClient } from '../../clients/adminClient';
import { Loading } from '../../components/common/Loading';
import { Notice } from '../../components/common/Notice';
import { Pager } from '../../components/common/Pager';
import { PRODUCT_LABEL } from '../../constants/labels';
import { ROUTES } from '../../constants/routes';
import { AdminLayout } from '../../layouts/AdminLayout';
import { useAdminPagedData } from '../../hooks/admin/useAdminPagedData';
import type { UiMessage } from '../../types/api';
import { money } from '../../util/format';
import { statusClass } from '../../util/status';

function numeric(value: string | null, fallback: number) {
  if (!value || !/^\d+$/.test(value)) return fallback;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : fallback;
}

export function AdminProductsPage() {
  const [params, setParams] = useSearchParams();
  const [notice, setNotice] = useState<UiMessage | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const q = params.get('q') || '';
  const category = params.get('category') || '';
  const status = params.get('status') || '';
  const page = numeric(params.get('page'), 0);
  const size = numeric(params.get('size'), 10);
  const key = JSON.stringify({ q, category, status, page, size });
  const { data, loading, error, refresh } = useAdminPagedData(
    () => adminClient.products({ q, category, status, page, size }), key);

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
    setParams({ q: String(form.get('q') || ''), category: String(form.get('category') || ''),
      status: String(form.get('status') || ''), size: String(form.get('size') || '10'), page: '0' });
  }

  async function toggle(id: number, name: string, published: boolean) {
    if (busyId !== null || !window.confirm(`${published ? 'Ẩn' : 'Hiển thị'} thực đơn "${name}"?`)) return;
    setBusyId(id);
    setNotice(null);
    try {
      await adminClient.toggleProduct(id);
      setNotice({ type: 'success', text: published ? 'Đã ẩn thực đơn.' : 'Đã hiển thị thực đơn.' });
      refresh();
    } catch (e) {
      setNotice({ text: e instanceof Error ? e.message : 'Không thể thay đổi trạng thái thực đơn.' });
    } finally { setBusyId(null); }
  }

  return <AdminLayout title="Quản lý thực đơn" lead="Quản lý giá, trạng thái và dữ liệu thực đơn.">
    <Notice message={notice} />
    <div className="between"><span /><Link className="btn" to={ROUTES.ADMIN_PRODUCT_NEW}>+ Thêm thực đơn</Link></div>
    <form key={key} className="card grid grid-4" onSubmit={apply}>
      <label className="field">Tìm thực đơn<input name="q" defaultValue={q} /></label>
      <label className="field">Danh mục<select name="category" defaultValue={category}><option value="">Tất cả</option>
        {[...new Set([...new Set(data?.categories ?? []), category].filter(Boolean))].map((value) =>
          <option key={value} value={value}>{value}</option>)}</select></label>
      <label className="field">Trạng thái<select name="status" defaultValue={status}><option value="">Tất cả</option>
        {['PUBLISHED','DRAFT','HIDDEN'].map((value) => <option key={value} value={value}>{PRODUCT_LABEL[value]}</option>)}</select></label>
      <label className="field">Số dòng<select name="size" defaultValue={size}>
        {[10,20,50].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
      <button className="btn" type="submit">Lọc thực đơn</button>
    </form>
    {loading ? <Loading /> : error ? <section className="card stack"><p role="alert">{error}</p>
      <button className="btn secondary" onClick={refresh}>Thử lại</button></section> : data && (
      <section className="card stack"><div className="table-wrap"><table>
        <caption>Hiển thị {data.numberOfElements} / {data.totalElements} thực đơn</caption>
        <thead><tr><th>Mã</th><th>Tên</th><th>Danh mục</th><th>Giá/người</th><th>Số khách</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
        <tbody>{data.content.length === 0 && <tr><td colSpan={7}>Không có thực đơn phù hợp.</td></tr>}
          {data.content.map((product) => <tr key={product.id}>
            <td>{product.displayCode || `SP-${product.id}`}</td><td><strong>{product.name}</strong></td>
            <td>{product.category}</td><td>{money(product.unitPrice)}</td>
            <td>{product.minGuests}–{product.maxGuests}</td>
            <td><span className={`badge ${statusClass(product.status)}`}>{PRODUCT_LABEL[product.status] || product.status}</span></td>
            <td><div className="row"><Link className="text-link" to={`${ROUTES.ADMIN_PRODUCT_EDIT}?id=${product.id}`}>Sửa</Link>
              <button className="text-link" type="button" disabled={busyId !== null}
                onClick={() => void toggle(product.id, product.name, product.status === 'PUBLISHED')}>
                {product.status === 'PUBLISHED' ? 'Ẩn' : 'Hiện'}</button></div></td>
          </tr>)}</tbody></table></div>
        <Pager data={data} onPage={(next) => setParams({ q, category, status, size: String(size), page: String(next) })} />
      </section>
    )}
  </AdminLayout>;
}
