import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authClient } from '../../clients/authClient';
import { AuthShell } from '../../components/auth/AuthShell';
import { Notice } from '../../components/common/Notice';
import { ROUTES } from '../../constants/routes';
import { usePasswordReset } from '../../hooks/auth/usePasswordReset';
import type { UiMessage } from '../../types/api';
import { authErrorMessage } from '../../util/authError';
import { normalizeEmail } from '../../validations/authValidation';

export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const { startOtpVerification } = usePasswordReset();
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState<UiMessage | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const cleanEmail = normalizeEmail(email);
      await authClient.forgotPassword(cleanEmail);
      startOtpVerification(cleanEmail);
      setMessage({ type: 'success', text: `Nếu tài khoản tồn tại và đủ điều kiện, mã khôi phục sẽ được gửi tới ${cleanEmail}.` });
      window.setTimeout(() => navigate(`${ROUTES.RESET_PASSWORD}?email=${encodeURIComponent(cleanEmail)}`), 400);
    } catch (error) {
      setMessage({ text: authErrorMessage(error, 'Không thể gửi mã khôi phục.') });
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Quên mật khẩu">
      <div><span className="badge">Khôi phục tài khoản</span><h1>Quên mật khẩu?</h1><p className="muted">Nhập email đã đăng ký để nhận OTP.</p></div>
      <Notice message={message} />
      <form className="stack" onSubmit={submit}>
        <label className="field">Email đã đăng ký<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="ban@example.com" required /></label>
        <button className="btn" disabled={busy}>{busy ? 'Đang gửi OTP...' : 'Gửi mã khôi phục'}</button>
      </form>
      <Link className="text-link" to={ROUTES.LOGIN}>Về đăng nhập</Link>
    </AuthShell>
  );
}
