import type { ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { Brand } from '../components/common/Brand';
import { NotificationBell } from '../components/notifications/NotificationBell';
import { ROUTES } from '../constants/routes';

const adminLinks = [
  [ROUTES.ADMIN, 'Tổng quan'],
  [ROUTES.ADMIN_USERS, 'Người dùng'],
  [ROUTES.ADMIN_PRODUCTS, 'Sản phẩm'],
  [ROUTES.ADMIN_ORDERS, 'Đơn hàng'],
  [ROUTES.ADMIN_STATISTICS, 'Thống kê'],
  [ROUTES.ADMIN_SITE_SETTINGS, 'Giao diện & nội dung website'],
  [ROUTES.ADMIN_NOTIFICATIONS, 'Gửi thông báo'],
  [ROUTES.ADMIN_FAQS, 'Câu hỏi thường gặp'],
  [ROUTES.ADMIN_COOKING_REQUESTS, 'Yêu cầu nấu ăn riêng'],
  ['/admin/manage/customers', 'Hồ sơ khách hàng'],
  ['/admin/manage/chefs', 'Đầu bếp & xác minh'],
  ['/admin/manage/payments', 'Giao dịch'],
  ['/admin/manage/refunds', 'Yêu cầu hoàn tiền'],
  ['/admin/manage/vouchers', 'Voucher'],
  ['/admin/manage/cases', 'Khiếu nại'],
  ['/admin/manage/reviews', 'Kiểm duyệt đánh giá'],
  ['/admin/manage/tickets', 'Hỗ trợ'],
  ['/admin/manage/audit', 'Nhật ký thao tác'],
] as const;

export function AdminLayout({ title, lead, children }: { title: string; lead?: string; children: ReactNode }) {
  return (
    <>
      <header className="site-header">
        <div className="container header-inner">
          <Brand admin />
          <nav className="nav admin-top-nav" aria-label="Điều hướng quản trị">
            {adminLinks.slice(0, 5).map(([to, label]) => <NavLink key={to} end={to === ROUTES.ADMIN} to={to}>{label}</NavLink>)}
          </nav>
          <div className="header-actions admin-header-actions">
            <NotificationBell />
            <Link className="btn secondary sm" to={ROUTES.ACCOUNT}>Sửa hồ sơ</Link>
            <Link className="btn secondary sm" to="/">Về trang chủ</Link>
            <Link className="btn sm admin-logout-action" to={ROUTES.LOGOUT} aria-label="Đăng xuất tài khoản quản trị">Đăng xuất</Link>
          </div>
        </div>
      </header>

      <main id="main">
        <div className="container">
          <div className="page-heading">
            <nav className="breadcrumb" aria-label="Breadcrumb"><Link to={ROUTES.ADMIN}>Quản trị</Link><span>/</span><span>{title}</span></nav>
            <h1>{title}</h1>
            {lead && <p className="lead">{lead}</p>}
          </div>
          <div className="workspace admin-workspace">
            <nav className="card side-nav admin-side-nav" aria-label="Quản lý">
              <p className="eyebrow">Quản trị hệ thống</p>
              {adminLinks.map(([to, label]) => <NavLink key={to} end={to === ROUTES.ADMIN} to={to}>{label}</NavLink>)}
              <Link className="admin-side-logout" to={ROUTES.LOGOUT}>Đăng xuất</Link>
            </nav>
            <div className="stack admin-main">{children}</div>
          </div>
        </div>
      </main>

      <footer className="site-footer"><div className="container"><div className="footer-bottom"><span>© 2026 GO Cook · Nhóm 2-1</span><span>Kênh quản trị React</span></div></div></footer>
    </>
  );
}
