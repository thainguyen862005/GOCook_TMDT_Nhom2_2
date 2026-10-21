import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { adminClient } from '../../clients/adminClient';
import { Loading } from '../../components/common/Loading';
import { Notice } from '../../components/common/Notice';
import { Pager } from '../../components/common/Pager';
import { ROLE_LABEL } from '../../constants/labels';
import { ROUTES } from '../../constants/routes';
import { AdminLayout } from '../../layouts/AdminLayout';
import { useAdminPagedData } from '../../hooks/admin/useAdminPagedData';
import type { UiMessage } from '../../types/api';

function numberParam(value: string | null, fallback: number) {
  if (!value || !/^\d+$/.test(value)) return fallback;
  const number = Number(value);
  return Number.isSafeInteger(number) ? number : fallback;
}

export function AdminUsersPage() {
  const [params, setParams] = useSearchParams();
  const [notice, setNotice] = useState<UiMessage | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const q = params.get('q') || '';
  const role = params.get('role') || '';
  const status = params.get('status') || '';
  const page = numberParam(params.get('page'), 0);
  const size = numberParam(params.get('size'), 10);
  const key = JSON.stringify({ q, role, status, page, size });
  const { data, loading, error, refresh } = useAdminPagedData(
    () => adminClient.users({ q, role, status, page, size }), key);

  // Clamp when filtering makes the formerly selected page invalid.
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
    setParams({ q: String(form.get('q') || ''), role: String(form.get('role') || ''),
      status: String(form.get('status') || ''), size: String(form.get('size') || '10'), page: '0' });
  }

  async function toggle(id: number, name: string, active: boolean) {
    if (busyId !== null || !window.confirm(`${active ? 'Khóa' : 'Mở khóa'} tài khoản "${name}"?`)) return;
    setBusyId(id);
    setNotice(null);
    try {
      await adminClient.toggleUser(id);
      setNotice({ type: 'success', text: active ? 'Đã khóa tài khoản.' : 'Đã mở khóa tài khoản.' });
      refresh();
    } catch (e) {
      setNotice({ text: e instanceof Error ? e.message : 'Không thể cập nhật tài khoản.' });
    } finally { setBusyId(null); }
  }

  return <AdminLayout title="Quản lý người dùng" lead="Tìm kiếm, phân quyền và khóa/mở tài khoản.">
    <Notice message={notice} />
    <form key={key} className="card grid grid-4" onSubmit={apply}>
      <label className="field">Tìm người dùng<input name="q" defaultValue={q} placeholder="Tên hoặc email" /></label>
      <label className="field">Vai trò<select name="role" defaultValue={role}><option value="">Tất cả</option>
        {['USER','INSTRUCTOR','ADMIN','COMPANY_ADMIN'].map((value) => <option key={value} value={value}>{ROLE_LABEL[value] || value}</option>)}</select></label>
      <label className="field">Trạng thái<select name="status" defaultValue={status}><option value="">Tất cả</option>
        <option value="active">Hoạt động</option><option value="inactive">Đã khóa</option></select></label>
      <label className="field">Số dòng<select name="size" defaultValue={size}>
        {[10,20,50].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
      <button className="btn" type="submit">Lọc người dùng</button>
    </form>
    {loading ? <Loading /> : error ? <section className="card stack"><p role="alert">{error}</p>
      <button className="btn secondary" onClick={refresh}>Thử lại</button></section> : data && (
      <section className="card stack"><div className="table-wrap"><table>
        <caption>Hiển thị {data.numberOfElements} / {data.totalElements} người dùng</caption>
        <thead><tr><th>Người dùng</th><th>Email</th><th>Vai trò</th><th>Trạng thái</th><th>Ngày tạo</th><th>Thao tác</th></tr></thead>
        <tbody>{data.content.length === 0 && <tr><td colSpan={6}>Không có tài khoản phù hợp.</td></tr>}
        {data.content.map((user) => <tr key={user.id}>
          <td><strong>{user.username}</strong></td><td>{user.email}</td>
          <td>{user.roleLabel || ROLE_LABEL[user.role]}</td>
          <td><span className={`badge ${user.active ? '' : 'red'}`}>{user.active ? 'Hoạt động' : 'Đã khóa'}</span></td>
          <td>{user.createdAtDisplay || '—'}</td>
          <td><div className="row"><Link className="text-link" to={`${ROUTES.ADMIN_USER_EDIT}?id=${user.id}`}>Sửa</Link>
            <button type="button" className="text-link" disabled={busyId !== null}
              onClick={() => void toggle(user.id, user.username, user.active)}>{user.active ? 'Khóa' : 'Mở'}</button></div></td>
        </tr>)}</tbody></table></div>
        <Pager data={data} onPage={(next) => setParams({ q, role, status, size: String(size), page: String(next) })} />
      </section>
    )}
  </AdminLayout>;
}
