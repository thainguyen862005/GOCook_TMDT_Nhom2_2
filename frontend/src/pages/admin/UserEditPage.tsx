import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { adminClient } from '../../clients/adminClient';
import { Loading } from '../../components/common/Loading';
import { Notice } from '../../components/common/Notice';
import { ROLE_LABEL } from '../../constants/labels';
import { ROUTES } from '../../constants/routes';
import { AdminLayout } from '../../layouts/AdminLayout';
import type { AdminUser } from '../../types/admin';
import type { UiMessage } from '../../types/api';

interface UserForm { username: string; roles: string[]; active: boolean }

export function AdminUserEditPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const id = params.get('id');
  const [data, setData] = useState<{ user: AdminUser; roles: string[] } | null>(null);
  const [form, setForm] = useState<UserForm | null>(null);
  const [message, setMessage] = useState<UiMessage | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setData(null);
    setForm(null);
    setMessage(null);
    setLoading(true);
    if (!id || !/^[1-9]\d*$/.test(id)) {
      setMessage({ text: 'Mã tài khoản không hợp lệ.' });
      setLoading(false);
      return;
    }
    adminClient.user(id).then((result) => {
      if (!active) return;
      setData(result);
      setForm({
        username: result.user.username,
        roles: result.user.roles ?? [result.user.role],
        active: result.user.active,
      });
    }).catch((error: Error) => {
      if (active) setMessage({ text: error.message });
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [id]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!id || !form || busy) return;
    const username = form.username.trim();
    if (!username || username.length > 255) {
      setMessage({ text: 'Tên hiển thị phải từ 1 đến 255 ký tự.' });
      return;
    }
    if (form.roles.length === 0) {
      setMessage({ text: 'Chọn ít nhất một vai trò.' });
      return;
    }
    const oldRoles = data?.user.roles ?? (data?.user.role ? [data.user.role] : []);
    if (oldRoles.includes('ADMIN') && (!form.roles.includes('ADMIN') || !form.active)
        && !window.confirm('Thao tác này thay đổi quyền/trạng thái Admin. Tiếp tục?')) return;
    setBusy(true);
    setMessage(null);
    try {
      await adminClient.updateUser(id, { username, roles: form.roles, active: form.active });
      navigate(ROUTES.ADMIN_USERS, { replace: true });
    } catch (error) {
      setMessage({ text: error instanceof Error ? error.message : 'Không thể cập nhật tài khoản.' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <AdminLayout title="Chỉnh sửa người dùng" lead="Phân quyền nhiều vai trò, cập nhật tên và khóa/mở tài khoản.">
      <Notice message={message} />
      {loading ? <Loading /> : !form || !data ? (
        <section className="card stack"><p>Không thể tải thông tin người dùng.</p>
          <Link className="btn secondary" to={ROUTES.ADMIN_USERS}>Về danh sách</Link></section>
      ) : (
        <form className="card stack" onSubmit={save}>
          <label className="field">Email<input value={data.user.email} readOnly /></label>
          <label className="field">Tên hiển thị<input value={form.username} maxLength={255} required disabled={busy}
            onChange={(event) => setForm({ ...form, username: event.target.value })} /></label>
          <fieldset className="stack-sm" style={{ border: '1px solid #d9ddcf', borderRadius: 12, padding: 16 }}>
            <legend style={{ padding: '0 8px' }}>Vai trò (có thể chọn nhiều)</legend>
            {data.roles.map((role) => (
              <label className="check" key={role}>
                <input type="checkbox" disabled={busy} checked={form.roles.includes(role)}
                  onChange={(event) => setForm({ ...form, roles: event.target.checked
                    ? [...new Set([...form.roles, role])]
                    : form.roles.filter((value) => value !== role) })} />
                <span>{ROLE_LABEL[role] || role}</span>
              </label>
            ))}
          </fieldset>
          <label className="check"><input type="checkbox" checked={form.active} disabled={busy}
            onChange={(event) => setForm({ ...form, active: event.target.checked })} />
            <span>Tài khoản đang hoạt động</span></label>
          <div className="row"><button className="btn" type="submit" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu thay đổi'}</button>
            <Link className="btn secondary" to={ROUTES.ADMIN_USERS}>Hủy</Link></div>
        </form>
      )}
    </AdminLayout>
  );
}
