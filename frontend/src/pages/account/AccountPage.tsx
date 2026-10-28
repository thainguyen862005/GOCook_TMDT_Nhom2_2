import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { userClient } from '../../clients/userClient';
import { AccountSidebar } from '../../components/account/AccountSidebar';
import { Notice } from '../../components/common/Notice';
import { SingleImageField } from '../../components/common/SingleImageField';
import { GoogleAuthButton } from '../../components/auth/GoogleAuthButton';
import { authClient } from '../../clients/authClient';
import { useCallback } from 'react';
import { ROUTES } from '../../constants/routes';
import { useAuth } from '../../hooks/auth/useAuth';
import type { UiMessage } from '../../types/api';
import { initials } from '../../util/format';
import { validatePasswordPair } from '../../validations/authValidation';

/** Account feature page. Shared site header/footer come from customerRoutes -> SiteLayout. */
export function AccountPage() {
  const { profile, refreshProfile } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState(profile?.username || '');
  const [phone, setPhone] = useState(profile?.phone || '');
  const [defaultAddress, setDefaultAddress] = useState(profile?.defaultAddress || '');
  const [message, setMessage] = useState<UiMessage | null>(null);
  const [busy, setBusy] = useState(false);
  const [avatar, setAvatar] = useState<File | null>(null);
  const [passwords, setPasswords] = useState({ current: '', next: '', confirm: '' });
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [googleMessage, setGoogleMessage] = useState<UiMessage | null>(null);
  const [passwordMessage, setPasswordMessage] = useState<UiMessage | null>(null);

  useEffect(() => {
    setUsername(profile?.username || '');
    setPhone(profile?.phone || '');
    setDefaultAddress(profile?.defaultAddress || '');
  }, [profile?.username, profile?.phone, profile?.defaultAddress]);

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      await userClient.updateProfile(username.trim(), phone.trim(), defaultAddress.trim());
      await refreshProfile();
      setMessage({ type: 'success', text: 'Đã cập nhật hồ sơ cá nhân.' });
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

  async function changePassword(event: FormEvent) {
    event.preventDefault();
    const error = validatePasswordPair(passwords.next, passwords.confirm);
    if (error) return setPasswordMessage({ text: error });
    setPasswordBusy(true);
    setPasswordMessage(null);
    try {
      await userClient.changePassword(passwords.current, passwords.next, passwords.confirm);
      setPasswords({ current: '', next: '', confirm: '' });
      setPasswordMessage({ type: 'success', text: 'Đã đổi mật khẩu. Các phiên đăng nhập cũ đã hết hiệu lực. Vui lòng đăng nhập lại.' });
      await refreshProfile();
    } catch (error) {
      setPasswordMessage({ text: error instanceof Error ? error.message : 'Không thể đổi mật khẩu.' });
    } finally { setPasswordBusy(false); }
  }

  const googleLinked = useCallback(async () => {
    await refreshProfile();
    setGoogleMessage({ type: 'success', text: 'Đã liên kết tài khoản Google.' });
  }, [refreshProfile]);

  async function setInitialPassword(event: FormEvent) {
    event.preventDefault();
    const error = validatePasswordPair(passwords.next, passwords.confirm);
    if (error) return setPasswordMessage({ text: error });
    setPasswordBusy(true);
    setPasswordMessage(null);
    try {
      await authClient.setInitialPassword(passwords.next, passwords.confirm);
      setPasswords({ current: '', next: '', confirm: '' });
      setPasswordMessage({ type: 'success', text: 'Đã tạo mật khẩu thủ công. Hãy đăng nhập lại bằng email và mật khẩu.' });
      navigate(`${ROUTES.LOGIN}?passwordSet=1`, { replace: true });
    } catch (reason) {
      setPasswordMessage({ text: reason instanceof Error ? reason.message : 'Không thể tạo mật khẩu.' });
    } finally { setPasswordBusy(false); }
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
            <label className="field">Số điện thoại (không bắt buộc)
              <input type="tel" maxLength={20} autoComplete="tel" value={phone} placeholder="0901234567"
                onChange={(event) => setPhone(event.target.value)} />
            </label>
            <label className="field">Địa chỉ mặc định (không bắt buộc)
              <textarea maxLength={500} value={defaultAddress} autoComplete="street-address"
                onChange={(event) => setDefaultAddress(event.target.value)} />
            </label>
          </section>
          <button className="btn" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu thay đổi'}</button>
        </form>
        <div className="stack">
          <section className="card stack">
            <h2>Bảo mật tài khoản</h2>
            <p className="muted">{profile?.emailVerified
              ? 'Email đã xác thực. Tài khoản có thể tiếp tục quy trình đặt dịch vụ.'
              : 'Email đang chờ xác thực. Bạn có thể xem thực đơn; cần nhập OTP khi đặt dịch vụ.'}</p>
            {!profile?.emailVerified && profile?.email && (
              <Link className="btn secondary" to={`${ROUTES.VERIFY_EMAIL}?email=${encodeURIComponent(profile.email)}`}>
                Xác thực email khi bạn sẵn sàng
              </Link>
            )}
          </section>
          <section className="card stack">
            <h2>Đăng nhập Google</h2>
            <p className="muted">{profile?.googleLinked
              ? 'Tài khoản này đã được liên kết Google.'
              : 'Có thể liên kết Google sau khi đã đăng nhập bằng mật khẩu. Email Google phải trùng email tài khoản.'}</p>
            <Notice message={googleMessage} />
            {!profile?.googleLinked && <GoogleAuthButton mode="link" onSuccess={googleLinked} />}
          </section>
          <form className="card stack" onSubmit={profile?.passwordLoginEnabled === false ? setInitialPassword : changePassword}>
            <h2>{profile?.passwordLoginEnabled === false ? 'Tạo mật khẩu đăng nhập thủ công' : 'Đổi mật khẩu'}</h2>
            <Notice message={passwordMessage} />
            {profile?.passwordLoginEnabled !== false && <label className="field">Mật khẩu hiện tại
              <input type="password" autoComplete="current-password" required value={passwords.current}
                onChange={(e) => setPasswords({ ...passwords, current: e.target.value })} />
            </label>}
            <label className="field">Mật khẩu mới (12–128 ký tự)
              <input type="password" minLength={12} maxLength={128} autoComplete="new-password" required value={passwords.next}
                onChange={(e) => setPasswords({ ...passwords, next: e.target.value })} />
            </label>
            <label className="field">Nhập lại mật khẩu mới
              <input type="password" minLength={12} maxLength={128} autoComplete="new-password" required value={passwords.confirm}
                onChange={(e) => setPasswords({ ...passwords, confirm: e.target.value })} />
            </label>
            <button className="btn secondary" disabled={passwordBusy}>{passwordBusy ? 'Đang lưu...' : profile?.passwordLoginEnabled === false ? 'Tạo mật khẩu' : 'Đổi mật khẩu'}</button>
          </form>
        </div>
      </div>
    </div>
  );
}
