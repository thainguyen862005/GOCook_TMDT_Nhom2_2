import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { AuthShell } from "../../components/auth/AuthShell";
import { GoogleAuthButton } from "../../components/auth/GoogleAuthButton";
import { useCallback } from "react";
import { Notice } from "../../components/common/Notice";
import { loginDestination } from "../../util/loginDestination";
import { ROUTES } from "../../constants/routes";
import { useAuth } from "../../hooks/auth/useAuth";
import type { UiMessage } from "../../types/api";
import { authErrorMessage } from "../../util/authError";
import { normalizeEmail } from "../../validations/authValidation";

export function LoginPage() {
  const { login, refreshProfile } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [form, setForm] = useState({ email: "", password: "" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<UiMessage | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const email = normalizeEmail(form.email);
      const profile = await login(email, form.password);
      if (!profile)
        throw new Error("Không thể tải tài khoản. Vui lòng đăng nhập lại.");
      const target = loginDestination(
        profile.roles ?? [],
        (location.state as { from?: string } | null)?.from,
      );
      setMessage({ type: "success", text: "Đăng nhập thành công." });
      window.setTimeout(() => navigate(target, { replace: true }), 200);
    } catch (error) {
      setMessage({ text: authErrorMessage(error, "Không thể đăng nhập.") });
    } finally {
      setBusy(false);
    }
  }

  const googleSuccess = useCallback(async () => {
    const profile = await refreshProfile();
    if (!profile) throw new Error('Không thể tải hồ sơ sau khi đăng nhập Google.');
    navigate(loginDestination(profile.roles ?? [], (location.state as { from?: string } | null)?.from), { replace: true });
  }, [refreshProfile, navigate, location.state]);

  return (
    <AuthShell title="Đăng nhập">
      <nav className="tabs">
        <Link aria-current="page" to={ROUTES.LOGIN}>
          Đăng nhập
        </Link>
        <Link to={ROUTES.REGISTER}>Đăng ký</Link>
      </nav>
      <div>
        <h1>Chào bạn trở lại!</h1>
        <p className="muted">Bữa cơm ngon tiếp theo đang chờ bạn.</p>
      </div>
      {params.get("passwordSet") === "1" && <Notice message={{ type: "success", text: "Đã tạo mật khẩu. Bây giờ bạn có thể đăng nhập thủ công bằng email." }} />}
      {params.get("verified") === "1" && <Notice message={{ type: "success", text: "Email đã được xác thực. Vui lòng đăng nhập." }} />}
      <Notice message={message} />
      <form className="stack" onSubmit={submit}>
        <label className="field">
          Email
          <input
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            placeholder="ban@example.com"
            autoComplete="username"
            required
          />
        </label>
        <label className="field">
          Mật khẩu
          <input
            type="password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            placeholder="Nhập mật khẩu"
            autoComplete="current-password"
            required
          />
        </label>
        <div className="between">
          <span />
          <Link className="text-link small" to={ROUTES.FORGOT_PASSWORD}>
            Quên mật khẩu?
          </Link>
        </div>
        <button className="btn full" disabled={busy}>
          {busy ? "Đang đăng nhập..." : "Đăng nhập"}
        </button>
      </form>
      <div className="stack">
        <GoogleAuthButton mode="signin" onSuccess={googleSuccess} />
      </div>
      <p style={{textAlign: "center"}} className="small muted">
        Bạn mới đến?{" "}
        <Link className="text-link" to={ROUTES.REGISTER}>
          Tạo tài khoản
        </Link>
      </p>
    </AuthShell>
  );
}
