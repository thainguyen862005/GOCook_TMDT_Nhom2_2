import { useEffect, type ReactNode } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import "../../styles/seller-preview.css";
import { useAuth } from "../../hooks/auth/useAuth";

const links = [
  [
    "/seller",
    "Tổng quan",
    "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
  ],
  [
    "/seller/products",
    "Thực đơn của tôi",
    "M3 17h18 M5 17a7 7 0 0 1 14 0 M12 8V4 M4 21h16",
  ],
  [
    "/seller/orders",
    "Đơn đặt nấu",
    "M4 5h16v16H4z M8 3v4 M16 3v4 M4 10h16 M8 14h8",
  ],
  ["/seller/revenue", "Doanh thu", "M4 3v18h17 M8 17v-5 M13 17V7 M18 17V4"],
  [
    "/seller/schedule",
    "Lịch phục vụ",
    "M4 5h16v16H4z M8 3v4 M16 3v4 M4 10h16 M8 14h2 M14 14h2",
  ],
  ["/messages", "Hộp thư", "M21 4H3v16l5-4h13z M7 8h10 M7 12h6"],
  ["/seller/requests", "Yêu cầu & báo giá", "M21 4H3v16l5-4h13z M7 8h10 M7 12h6"],
  ["/seller/profile", "Hồ sơ đầu bếp", "M12 12a4 4 0 1 0 0-8a4 4 0 0 0 0 8 M4 21v-3a8 8 0 0 1 16 0v3"],
];
export function SellerWorkspace({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  const { pathname } = useLocation();
  const { profile } = useAuth();
  const chefName = profile?.username || "Đầu bếp";
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "instant" });
  }, [pathname]);
  return (
    <div className="container chef-preview">
      <div className="page-heading">
        <nav className="breadcrumb" aria-label="Đường dẫn">
          <Link to="/">Trang chủ</Link>
          <span>/</span>
          <span>Kênh đầu bếp</span>
        </nav>
        <div className="between chef-section-heading">
          <div>
            <p className="eyebrow">{chefName} · Kênh đầu bếp</p>
            <h1>{title}</h1>
            <p className="lead">{description}</p>
          </div>
          <Link className="btn secondary" to="/seller/product-new">
            + Thêm thực đơn
          </Link>
        </div>
      </div>
      <div className="workspace">
        <aside className="card chef-sidebar">
          <div className="chef-identity">
            <span className="chef-avatar">
              {chefName.charAt(0).toLocaleUpperCase("vi-VN")}
            </span>
            <div>
              <strong>{chefName}</strong>
              <p className="small muted">Đầu bếp tại gia</p>
            </div>
          </div>
          <nav className="side-nav" aria-label="Quản lý đầu bếp">
            {links.map(([to, label, path]) => (
              <NavLink key={to} to={to} end={to === "/seller"}>
                <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path d={path} />
                </svg>
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="chef-sidebar-note">
            <p className="eyebrow">Bữa ngon, trọn niềm vui</p>
            <p className="small muted">
              Lịch rõ ràng, bếp thảnh thơi. Đừng quên cập nhật lịch nhận nấu của
              bạn.
            </p>
            <Link className="text-link small" to="/seller/schedule">
              Cập nhật lịch →
            </Link>
          </div>
        </aside>
        <div className="stack">{children}</div>
      </div>
    </div>
  );
}
