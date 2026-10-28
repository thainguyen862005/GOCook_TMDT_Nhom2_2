import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Notice } from '../../components/common/Notice';
import { ROUTES } from '../../constants/routes';
import { useAuth } from '../../hooks/auth/useAuth';
import { SiteLayout } from '../../layouts/SiteLayout';
import { authErrorMessage } from '../../util/authError';

type LogoutState = 'working' | 'success' | 'error';

export function LogoutPage() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState<LogoutState>('working');
  const [error, setError] = useState('');
  // React StrictMode remounts effects in development; reuse one request.
  const request = useRef<Promise<void> | null>(null);
  const [attempt, setAttempt] = useState(0);

  const start = useCallback(() => {
    if (!request.current) request.current = logout();
    return request.current;
  }, [logout]);

  useEffect(() => {
    let active = true;
    let redirect: ReturnType<typeof setTimeout> | undefined;
    void start().then(() => {
      if (!active) return;
      setStatus('success');
      redirect = setTimeout(() => navigate(ROUTES.LOGIN, { replace: true }), 300);
    }).catch((reason: unknown) => {
      if (!active) return;
      setStatus('error');
      setError(authErrorMessage(reason, 'Không thể đăng xuất. Vui lòng thử lại.'));
    });
    return () => {
      active = false;
      if (redirect) clearTimeout(redirect);
    };
  }, [start, navigate, attempt]);

  function retry() {
    request.current = null;
    setError('');
    setStatus('working');
    setAttempt((previous) => previous + 1);
  }

  return (
    <SiteLayout>
      <main id="main">
        <div className="container section">
          <section className="card empty stack" aria-live="polite">
            <h1>Đăng xuất</h1>
            {status === 'working' && <p className="muted">Đang kết thúc phiên đăng nhập...</p>}
            {status === 'success' && <p className="muted">Đã đăng xuất. Đang chuyển về trang đăng nhập...</p>}
            {status === 'error' && <>
              <Notice message={{ text: error }} />
              <p className="muted">Phiên đăng nhập có thể vẫn còn hiệu lực trên máy chủ. Hãy thử lại khi backend hoạt động.</p>
              <button className="btn" type="button" onClick={retry}>Thử đăng xuất lại</button>
              <Link className="text-link" to={ROUTES.HOME}>Về trang chủ</Link>
            </>}
          </section>
        </div>
      </main>
    </SiteLayout>
  );
}
