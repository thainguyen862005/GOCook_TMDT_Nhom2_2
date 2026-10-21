import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { userClient } from '../../clients/userClient';
import { AccountSidebar } from '../../components/account/AccountSidebar';
import { Notice } from '../../components/common/Notice';
import { SingleImageField } from '../../components/common/SingleImageField';
import { ROUTES } from '../../constants/routes';
import { useAuth } from '../../hooks/auth/useAuth';
import type { UiMessage } from '../../types/api';
import { initials } from '../../util/format';

/** Account feature page. Shared site header/footer come from customerRoutes -> SiteLayout. */
export function AccountPage() {
  const { profile, refreshProfile } = useAuth();
  const [username, setUsername] = useState(profile?.username || '');
  const [message, setMessage] = useState<UiMessage | null>(null);
  const [busy, setBusy] = useState(false);
  const [avatar, setAvatar] = useState<File | null>(null);

  useEffect(() => {
    setUsername(profile?.username || '');
  }, [profile?.username]);

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      await userClient.updateProfile(username.trim());
      await refreshProfile();
      setMessage({ type: 'success', text: 'Đã cập nhật tên hiển thị.' });
    } catch (error) {
      setMessage({ text: error instanceof Error ? error.message : 'Không thể cập nhật hồ sơ.' });
    } finally {
      setBusy(false);
    }
  }

  async function uploadAvatar() {
    if (!avatar || busy) return;
    setBusy(true); setMessage(null);
    try {
      await userClient.uploadAvatar(avatar);
      await refreshProfile();
      setAvatar(null);
      setMessage({ type: 'success', text: 'Ảnh đại diện đã được lưu trên Cloudinary.' });
    } catch (error) {
      setMessage({ text: error instanceof Error ? error.message : 'Không thể tải ảnh lên.' });
    } finally { setBusy(false); }
  }

  return (
    <div className="container">
      <div className="page-heading">
        <nav className="breadcrumb">
          <Link to={ROUTES.HOME}>Trang chủ</Link>
          <span>/</span>
          <span>Tài khoản của bạn</span>
        </nav>
        <h1>Tài khoản của bạn</h1>
        <p className="lead">Quản lý thông tin tài khoản GO Cook.</p>
      </div>

      <div className="workspace">
        <AccountSidebar active="account" />

        <form className="stack" onSubmit={save}>
          <section className="card stack">
            <div className="row">
              {profile?.avatarUrl ? <img className="avatar large" src={profile.avatarUrl} alt="Ảnh đại diện hiện tại" />
                : <span className="avatar large">{initials(profile?.username)}</span>}
              <div>
                <h3>{profile?.username}</h3>
                <p className="small muted">{profile?.email}</p>
              </div>
            </div>
            <Notice message={message} />
            <SingleImageField title="Ảnh đại diện mới" value={avatar} onChange={setAvatar} disabled={busy} />
            <div><button type="button" className="btn secondary sm" disabled={busy || !avatar} onClick={uploadAvatar}>
              {busy ? 'Đang tải ảnh...' : 'Tải ảnh đại diện lên Cloudinary'}
            </button></div>
            <div className="grid grid-2">
              <label className="field">
                Họ và tên
                <input value={username} onChange={(event) => setUsername(event.target.value)} required />
              </label>
              <label className="field">
                Email
                <input value={profile?.email || ''} readOnly />
              </label>
            </div>
          </section>
          <button className="btn" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu thay đổi'}</button>
        </form>
      </div>
    </div>
  );
}
