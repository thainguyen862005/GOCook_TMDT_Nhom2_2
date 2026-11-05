import type { ReactNode } from "react";
import { ChatbotWidget } from "../components/chatbot/ChatbotWidget";
import { SiteSettingsProvider, useSiteSettings } from "../contexts/SiteSettingsContext";
import { Link, NavLink, Outlet } from "react-router-dom";
import { Brand } from "../components/common/Brand";
import { NotificationBell } from "../components/notifications/NotificationBell";
import { ROUTES } from "../constants/routes";
import { useAuth } from "../hooks/auth/useAuth";

/** Compact action icons keep the header readable while preserving full labels for assistive technology. */
function HeaderGlyph({ kind }: { kind: 'message' | 'dashboard' | 'logout' | 'user' }) {
  if (kind === 'message') return <svg className="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 15a3 3 0 0 1-3 3H9l-6 3V6a3 3 0 0 1 3-3h11a3 3 0 0 1 3 3zM7 8h9M7 12h6" /></svg>;
  if (kind === 'dashboard') return <svg className="icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></svg>;
  if (kind === 'logout') return <svg className="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M10 3H4v18h6m-1-9h12m-4-4 4 4-4 4" /></svg>;
  return <svg className="icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4" /><path d="M4 21v-2a8 8 0 0 1 16 0v2" /></svg>;
}

function SiteHeader() {
  const { profile, isAdmin, isSeller } = useAuth();
  const accountUrl = profile ? ROUTES.ACCOUNT : ROUTES.LOGIN;
  const customerView = !isAdmin && !isSeller;

  return (
    <header className="site-header">
      <div className="container header-inner">
        <Brand />
        <nav className="nav" aria-label="Điều hướng chính">
          <NavLink end to={ROUTES.HOME}>
            Trang chủ
          </NavLink>
          <NavLink to={ROUTES.CHEFS}>Đầu bếp</NavLink>
          <NavLink to={ROUTES.MENUS}>Thực đơn</NavLink>
          <NavLink to={ROUTES.REQUEST}>Yêu cầu</NavLink>
          <NavLink to={ROUTES.OFFERS}>Ưu đãi</NavLink>
          <NavLink to={ROUTES.HELP}>Hỗ trợ</NavLink>
        </nav>
        <div className="header-actions">
          <NotificationBell />
          {!isAdmin && !isSeller && <Link className="header-cart-link" to={ROUTES.CART} aria-label="Mở giỏ hàng" title="Giỏ hàng">
            <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
              <path d="M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h8.7a2 2 0 0 0 2-1.6L23 6H6" />
            </svg>
            <span>Giỏ hàng</span>
          </Link>}
          {profile && !isAdmin && <Link className="header-utility-link header-messages-link" to={ROUTES.MESSAGES} aria-label="Tin nhắn" title="Tin nhắn"><HeaderGlyph kind="message" /></Link>}
          {isAdmin ? (
            <Link className="header-utility-link header-role-link" to={ROUTES.ADMIN} aria-label="Trang quản trị" title="Trang quản trị"><HeaderGlyph kind="dashboard" /></Link>
          ) : isSeller ? (
            <Link className="header-utility-link header-role-link" to={ROUTES.SELLER} aria-label="Kênh đầu bếp" title="Kênh đầu bếp"><HeaderGlyph kind="dashboard" /></Link>
          ) : null}
          <Link className="header-account-link" to={accountUrl} aria-label={profile ? 'Tài khoản của tôi' : 'Đăng nhập'} title={profile ? 'Tài khoản của tôi' : 'Đăng nhập'}>
            <span className="header-account-avatar" aria-hidden="true">{profile?.username ? profile.username.slice(0, 1).toLocaleUpperCase('vi-VN') : <HeaderGlyph kind="user" />}</span>
            <span className="header-account-name">{profile?.username || 'Đăng nhập'}</span>
          </Link>
          {profile && <Link className="session-logout-link header-utility-link header-logout-link" to={ROUTES.LOGOUT} aria-label="Đăng xuất" title="Đăng xuất"><HeaderGlyph kind="logout" /></Link>}
          {customerView && <Link className="btn sm header-book-link" to={ROUTES.CHEFS}>Đặt đầu bếp</Link>}
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
              {profile && <Link to={ROUTES.NOTIFICATIONS}>Thông báo của tôi</Link>}
              {!isAdmin && !isSeller && <Link to={ROUTES.CART}>Giỏ hàng</Link>}
              {profile && !isAdmin && <Link to={ROUTES.MESSAGES}>Hộp thư</Link>}
              {profile && <Link to={ROUTES.MY_REPORTS}>Báo cáo đã gửi</Link>}
              <Link to={accountUrl}>Tài khoản</Link>
              {isSeller && !isAdmin && <Link to={ROUTES.SELLER}>Kênh đầu bếp</Link>}
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
  const { profile, isSeller, isAdmin } = useAuth();
  const accountUrl = profile ? ROUTES.ACCOUNT : ROUTES.LOGIN;
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
              {!isSeller && !isAdmin && <Link to={ROUTES.CART}>Giỏ hàng</Link>}
              {!isSeller && !isAdmin && <Link to={ROUTES.MY_REQUESTS}>Yêu cầu nấu ăn</Link>}
              {!isSeller && !isAdmin && <Link to={ROUTES.ORDERS}>Đơn đặt nấu</Link>}
              {isSeller && <Link to={ROUTES.SELLER_ORDERS}>Đơn Chef</Link>}
              {profile && !isAdmin && <Link to={ROUTES.MESSAGES}>Hộp thư</Link>}
              {profile && <Link to={ROUTES.MY_REPORTS}>Báo cáo đã gửi</Link>}
              <Link to={accountUrl}>Tài khoản của tôi</Link>
              <Link to={ROUTES.BECOME_CHEF}>Trở thành đầu bếp</Link>
              {!profile || (isSeller && !isAdmin) ? <Link to={ROUTES.SELLER}>Kênh đầu bếp</Link> : null}
            </div>
            <div>
              <h4>Hỗ trợ & thông tin</h4>
              {site.facebookUrl && <a href={site.facebookUrl} target="_blank" rel="noopener noreferrer">Facebook</a>}
              {site.instagramUrl && <a href={site.instagramUrl} target="_blank" rel="noopener noreferrer">Instagram</a>}
              <Link to={ROUTES.HELP}>Câu hỏi thường gặp</Link>
              <Link to={ROUTES.HELP}>Liên hệ hỗ trợ</Link>
              {profile && profile.roles?.includes("ROLE_SYSTEM_ADMIN") && <Link to={ROUTES.ADMIN}>Kênh quản trị</Link>}
              <Link to={ROUTES.PAGE_CATALOG}>Danh mục màn hình</Link>
            </div>
          </div>
          <div className="footer-bottom">
            <span>{site.footerCopyright}</span>
            <span>{site.contactEmail && <a href={`mailto:${site.contactEmail}`}>{site.contactEmail}</a>} {site.contactPhone && <a href={`tel:${site.contactPhone}`}>{site.contactPhone}</a>}</span>
          </div>
        </div>
      </footer>
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
      <ChatbotWidget />
    </>
  );
}
