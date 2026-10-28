import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authClient } from '../../clients/authClient';
import { AuthShell } from '../../components/auth/AuthShell';
import { Notice } from '../../components/common/Notice';
import { ROUTES } from '../../constants/routes';
import { useAuth } from '../../hooks/auth/useAuth';
import type { UiMessage } from '../../types/api';
import { authErrorMessage } from '../../util/authError';
import { normalizeEmail, normalizeOtp } from '../../validations/authValidation';

export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { profile, refreshProfile } = useAuth();
  const requested = params.get('returnTo') || ROUTES.HOME;
  const returnTo = requested.startsWith('/') && !requested.startsWith('//') && !requested.includes('\\') ? requested : ROUTES.HOME;
  const autoSend = params.get('autoSend') === '1';
  const isTransactionalVerification = params.get('reason') === 'transaction';
  const isSignup = params.get('reason') === 'signup';

  const [email, setEmail] = useState(params.get('email') || profile?.email || '');
  const [otp, setOtp] = useState('');
  const [message, setMessage] = useState<UiMessage | null>(null);
  const [busy, setBusy] = useState(false);
  const autoSentRef = useRef(false);

  useEffect(() => {
    if (!email && profile?.email) setEmail(profile.email);
  }, [email, profile?.email]);

  useEffect(() => {
    if (!autoSend || autoSentRef.current || !email) return;
    autoSentRef.current = true;

    void (async () => {
      setBusy(true);
      try {
        const cleanEmail = normalizeEmail(email);
        await authClient.requestEmailVerification(cleanEmail);
        setMessage({
          type: 'success',
          text: `Nếu tài khoản đang chờ xác thực, mã sẽ được gửi tới ${cleanEmail}.`,
        });
      } catch (error) {
        setMessage({ text: authErrorMessage(error, 'Không thể gửi mã xác thực.') });
      } finally {
        setBusy(false);
      }
    })();
  }, [autoSend, email]);

  async function verify(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      await authClient.verifyEmail({ email: normalizeEmail(email), otp });
      const current = await refreshProfile();
      setMessage({ type: 'success', text: 'Xác thực email thành công. Bạn có thể tiếp tục.' });
      window.setTimeout(() => navigate(current ? returnTo : `${ROUTES.LOGIN}?verified=1`, { replace: true }), 350);
    } catch (error) {
      setMessage({ text: authErrorMessage(error, 'Không thể xác thực email.') });
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setBusy(true);
    setMessage(null);
    try {
      const cleanEmail = normalizeEmail(email);
      await authClient.requestEmailVerification(cleanEmail);
      setMessage({ type: 'success', text: `Nếu tài khoản đang chờ xác thực, mã mới sẽ được gửi tới ${cleanEmail}.` });
    } catch (error) {
      setMessage({ text: authErrorMessage(error, 'Không thể gửi lại OTP.') });
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Xác thực email">
      <div>
        <span className="badge">Xác thực email</span>
        <h1>{isSignup ? 'Đăng ký thành công — xác thực email' : isTransactionalVerification ? 'Xác thực trước khi đặt dịch vụ' : 'Nhập mã xác thực'}</h1>
        <p className="muted">
          {isSignup
            ? 'Chúng tôi đã gửi mã OTP tới email vừa đăng ký (nếu tài khoản mới). Mã có hiệu lực 10 phút. Bạn cũng có thể xác thực sau; tài khoản không bị xóa khi OTP hết hạn.'
            : isTransactionalVerification
              ? 'Bạn có thể xem GO Cook bình thường, nhưng phải xác thực email trước khi thanh toán.'
              : 'Mã OTP có hiệu lực trong 10 phút. Hết hạn hãy chọn Gửi lại mã.'}
        </p>
      </div>
      <Notice message={message} />
      <form className="stack" onSubmit={verify}>
        <label className="field">
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            readOnly={Boolean(profile?.email)}
            required
          />
        </label>
        <label className="field">
          Mã OTP
          <input
            value={otp}
            onChange={(e) => setOtp(normalizeOtp(e.target.value))}
            inputMode="numeric"
            pattern="[0-9]{6}"
            maxLength={6}
            required
          />
        </label>
        <button className="btn" disabled={busy}>
          {busy ? 'Đang xử lý...' : 'Xác thực và tiếp tục'}
        </button>
      </form>
      <button className="text-link" type="button" onClick={() => void resend()} disabled={busy}>
        Gửi lại mã xác thực
      </button>
      {isSignup && <Link className="text-link" to={ROUTES.LOGIN}>Xác thực sau — đăng nhập</Link>}
      <Link className="text-link" to={ROUTES.HOME}>Về trang chủ</Link>
    </AuthShell>
  );
}
