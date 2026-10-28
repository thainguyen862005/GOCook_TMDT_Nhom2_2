import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authClient } from '../../clients/authClient';
import { AuthShell } from '../../components/auth/AuthShell';
import { Notice } from '../../components/common/Notice';
import { ROUTES } from '../../constants/routes';
import { usePasswordReset } from '../../hooks/auth/usePasswordReset';
import type { UiMessage } from '../../types/api';
import { authErrorMessage } from '../../util/authError';
import { normalizeEmail, normalizeOtp, validatePasswordPair } from '../../validations/authValidation';

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const resetFlow = usePasswordReset();
  const initialEmail = normalizeEmail(params.get('email') || resetFlow.email);
  const [email, setEmail] = useState(initialEmail);
  const [otp, setOtp] = useState('');
  const [token, setToken] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [message, setMessage] = useState<UiMessage | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!initialEmail) return;
    setToken(resetFlow.restoreResetToken(initialEmail));
  }, [initialEmail, resetFlow.restoreResetToken]);

  async function verify(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const cleanEmail = normalizeEmail(email);
      const resetToken = await authClient.verifyResetOtp({ email: cleanEmail, otp });
      if (!resetToken) throw new Error('Không nhận được reset token.');
      resetFlow.acceptResetToken(cleanEmail, resetToken);
      setToken(resetToken);
      setMessage({ type: 'success', text: 'OTP hợp lệ. Hãy nhập mật khẩu mới.' });
    } catch (error) {
      setMessage({ text: authErrorMessage(error, 'OTP không hợp lệ.') });
    } finally {
      setBusy(false);
    }
  }

  async function reset(event: FormEvent) {
    event.preventDefault();
    const passwordError = validatePasswordPair(password, confirm);
    if (passwordError) return setMessage({ text: passwordError });

    setBusy(true);
    setMessage(null);
    try {
      await authClient.resetPassword({ resetPasswordToken: token, newPassword: password, confirmPassword: confirm });
      resetFlow.clearResetFlow();
      setMessage({ type: 'success', text: 'Đổi mật khẩu thành công.' });
      window.setTimeout(() => navigate(`${ROUTES.LOGIN}?reset=1`), 400);
    } catch (error) {
      setMessage({ text: authErrorMessage(error, 'Không thể đổi mật khẩu.') });
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Đặt lại mật khẩu">
      <div><span className="badge">Bảo mật tài khoản</span><h1>Đặt lại mật khẩu</h1></div>
      <Notice message={message} />
      {!token ? (
        <form className="stack" onSubmit={verify}>
          <label className="field">Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
          <label className="field">Mã OTP<input value={otp} onChange={(e) => setOtp(normalizeOtp(e.target.value))} pattern="[0-9]{6}" maxLength={6} required /></label>
          <button className="btn" disabled={busy}>{busy ? 'Đang xác minh...' : 'Xác minh OTP'}</button>
        </form>
      ) : (
        <form className="stack" onSubmit={reset}>
          <label className="field">Mật khẩu mới<input type="password" minLength={12} maxLength={128} value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
          <label className="field">Xác nhận mật khẩu<input type="password" minLength={12} maxLength={128} value={confirm} onChange={(e) => setConfirm(e.target.value)} required /></label>
          <button className="btn" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu mật khẩu mới'}</button>
        </form>
      )}
      <Link className="text-link" to={ROUTES.FORGOT_PASSWORD}>Không nhận được mã hoặc mã đã hết hạn? Gửi yêu cầu mới</Link>
      <Link className="text-link" to={ROUTES.LOGIN}>Về đăng nhập</Link>
    </AuthShell>
  );
}
