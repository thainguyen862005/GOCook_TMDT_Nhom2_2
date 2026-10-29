import { Link, NavLink } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import { useAuth } from '../../hooks/auth/useAuth';

type AccountSection = 'account' | 'orders' | 'chat' | 'password';

interface AccountSidebarProps {
  active?: AccountSection;
}

function UserIcon() {
  return (
    <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21v-2a8 8 0 0 1 16 0v2" />
    </svg>
  );
}

function OrderIcon() {
  return (
    <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 17h18M5 17a7 7 0 0 1 14 0M12 8V5m-2 0h4M4 21h16" />
    </svg>
  );
}

function ChatIcon() {
  return (
    <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M20 15a3 3 0 0 1-3 3H9l-6 3V6a3 3 0 0 1 3-3h11a3 3 0 0 1 3 3z" />
      <path d="M7 8h9M7 12h6" />
    </svg>
  );
}

function PasswordIcon() {
  return (
    <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d="m12 3 8 3v6c0 4-8 9-8 9s-8-5-8-9V6z" />
      <path d="m8 12 3 3 5-6" />
    </svg>
  );
}

function LogoutIcon() {
  return (
    <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M10 3H4v18h6m-1-9h12m-4-4 4 4-4 4" />
    </svg>
  );
}

export function AccountSidebar({ active }: AccountSidebarProps) {
  const { isAdmin, isSeller } = useAuth();
  return (
    <nav className="card side-nav" aria-label="Tài khoản">
      <p className="eyebrow">Góc của bạn</p>

      <NavLink end to={ROUTES.ACCOUNT} aria-current={active === 'account' ? 'page' : undefined}>
        <UserIcon />
        Tài khoản
      </NavLink>

      {!isAdmin && !isSeller && <NavLink to={ROUTES.ORDERS} aria-current={active === 'orders' ? 'page' : undefined}>
        <OrderIcon />
        Đơn đặt nấu
      </NavLink>}

      {!isAdmin && !isSeller && <NavLink to={ROUTES.MY_REQUESTS} aria-current={active === 'chat' ? 'page' : undefined}>
        <ChatIcon />
        Yêu cầu & tin nhắn
      </NavLink>}

      {!isAdmin && <NavLink to={ROUTES.MESSAGES}><ChatIcon />Hộp thư</NavLink>}
      {isSeller && <><NavLink to={ROUTES.SELLER}><OrderIcon />Kênh đầu bếp</NavLink><NavLink to={ROUTES.SELLER_PROFILE}><UserIcon />Hồ sơ Chef</NavLink></>}
      {isAdmin && <NavLink to={ROUTES.ADMIN}><OrderIcon />Kênh quản trị</NavLink>}

      <Link to={`${ROUTES.ACCOUNT}#account-security`}>
        <PasswordIcon />
        Đổi mật khẩu
      </Link>

      <NavLink to={ROUTES.LOGOUT}>
        <LogoutIcon />
        Đăng xuất
      </NavLink>
    </nav>
  );
}
