import type { ReactNode } from "react";
import { SiteSettingsProvider, useSiteSettings } from "../contexts/SiteSettingsContext";
import { Link, NavLink, Outlet } from "react-router-dom";
import { Brand } from "../components/common/Brand";
import { ROUTES } from "../constants/routes";
import { useAuth } from "../hooks/auth/useAuth";
import { accountDestination } from "../util/loginDestination";

function SiteHeader() {
  const { profile, isAdmin, isSeller } = useAuth();
  const accountUrl = profile
    ? accountDestination(profile.roles ?? [])
    : ROUTES.LOGIN;

  return (
    <header className="site-header">
      <div className="container header-inner">
        <Brand />
        <nav className="nav" aria-label="Điều hướng chính">
          <NavLink end to={ROUTES.HOME}>
            Trang chủ
          </NavLink>
          <NavLink to={ROUTES.CHEFS}>Khám phá đầu bếp</NavLink>
          <NavLink to={ROUTES.MENUS}>Thực đơn</NavLink>
          <NavLink to={ROUTES.REQUEST}>Tạo yêu cầu</NavLink>
          <NavLink to={ROUTES.OFFERS}>Ưu đãi</NavLink>
          <NavLink to={ROUTES.HELP}>Trợ giúp</NavLink>
        </nav>
        <div className="header-actions">
          <Link className="icon-link" to={ROUTES.CART} aria-label="Giỏ hàng">
            <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M3 3h2l3 12h10l3-9H6" />
              <circle cx="9" cy="20" r="1" />
              <circle cx="18" cy="20" r="1" />
            </svg>
          </Link>
          {isAdmin && (
            <Link className="text-link small" to={ROUTES.ADMIN}>
              Trang quản trị
            </Link>
          )}
          {isSeller && (
            <Link className="text-link small" to={ROUTES.SELLER}>
              Kênh đầu bếp
            </Link>
          )}
          <Link className="login-link text-link small" to={accountUrl}>
            {profile?.username || "Đăng nhập"}
          </Link>
          <Link className="btn sm" to={ROUTES.CHEFS}>
            Đặt đầu bếp
          </Link>
          <details className="mobile-menu">
            <summary aria-label="Mở menu">
              <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </summary>
            <nav aria-label="Điều hướng di động">
              <Link to={ROUTES.HOME}>Trang chủ</Link>
              <Link to={ROUTES.CHEFS}>Khám phá đầu bếp</Link>
              <Link to={ROUTES.MENUS}>Thực đơn</Link>
              <Link to={ROUTES.REQUEST}>Tạo yêu cầu</Link>
              <Link to={ROUTES.OFFERS}>Ưu đãi</Link>
              <Link to={ROUTES.HELP}>Trợ giúp</Link>
              <Link to={accountUrl}>
                {isSeller ? "Kênh đầu bếp" : "Tài khoản"}
              </Link>
              {isAdmin && <Link to={ROUTES.ADMIN}>Trang quản trị</Link>}
              <Link to={profile ? ROUTES.LOGOUT : ROUTES.LOGIN}>
                {profile ? "Đăng xuất" : "Đăng nhập"}
              </Link>
            </nav>
          </details>
        </div>
      </div>
    </header>
  );
}

function SiteFooter() {
  const site = useSiteSettings();
  const { profile } = useAuth();
  const accountUrl = profile
    ? accountDestination(profile.roles ?? [])
    : ROUTES.LOGIN;
  return (
    <>
      <footer className="site-footer">
        <div className="container">
          <div className="footer-grid">
            <div>
              <Brand />
              <p>
                {site.description}
              </p>
            </div>
            <div>
              <h4>Khám phá GO Cook</h4>
              <Link to={ROUTES.CHEFS}>Đầu bếp tại gia</Link>
              <Link to={ROUTES.MENUS}>Thực đơn có sẵn</Link>
              <Link to={ROUTES.REQUEST}>Đặt theo ngân sách</Link>
              <Link to={ROUTES.OFFERS}>Ưu đãi hôm nay</Link>
            </div>
            <div>
              <h4>Dành cho bạn</h4>
              <Link to={ROUTES.ORDERS}>Đơn đặt nấu</Link>
              <Link to={accountUrl}>Tài khoản của tôi</Link>
              <Link to={ROUTES.BECOME_CHEF}>Trở thành đầu bếp</Link>
              <Link to={ROUTES.SELLER}>Kênh đầu bếp</Link>
            </div>
            <div>
              <h4>Hỗ trợ & thông tin</h4>
              {site.facebookUrl && <a href={site.facebookUrl} target="_blank" rel="noopener noreferrer">Facebook</a>}
              {site.instagramUrl && <a href={site.instagramUrl} target="_blank" rel="noopener noreferrer">Instagram</a>}
              <Link to={ROUTES.HELP}>Câu hỏi thường gặp</Link>
              <Link to={ROUTES.CHAT}>Liên hệ hỗ trợ</Link>
              <Link to={ROUTES.ADMIN}>Kênh quản trị</Link>
              <Link to={ROUTES.PAGE_CATALOG}>Danh mục màn hình</Link>
            </div>
          </div>
          <div className="footer-bottom">
            <span>{site.footerCopyright}</span>
            <span>{site.contactEmail && <a href={`mailto:${site.contactEmail}`}>{site.contactEmail}</a>} {site.contactPhone && <a href={`tel:${site.contactPhone}`}>{site.contactPhone}</a>}</span>
          </div>
        </div>
      </footer>
      <Link className="help-float" to={ROUTES.CHAT} aria-label="Mở trò chuyện">
        <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20 15a3 3 0 0 1-3 3H9l-6 3V6a3 3 0 0 1 3-3h11a3 3 0 0 1 3 3z" />
          <path d="M7 8h9M7 12h6" />
        </svg>
      </Link>
    </>
  );
}

/**
 * Shared customer/public shell. With children it can also wrap auth-specific UI;
 * without children it renders the nested React Router <Outlet />.
 */
export function SiteLayout({ children }: { children?: ReactNode }) {
  return <SiteSettingsProvider><SiteLayoutInner>{children}</SiteLayoutInner></SiteSettingsProvider>;
}

function SiteLayoutInner({ children }: { children?: ReactNode }) {
  return (
    <>
      <a className="skip" href="#main">
        Đến nội dung chính
      </a>
      <SiteHeader />
      {children ?? (
        <main id="main">
          <Outlet />
        </main>
      )}
      <SiteFooter />
    </>
  );
}
