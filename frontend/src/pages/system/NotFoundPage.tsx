import { Link } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';

/** Never silently navigate an invalid booking/matching/chat path back home. */
export function NotFoundPage() {
  return <main id="main" className="container section">
    <section className="card stack missing-route" role="alert">
      <span className="eyebrow">404 · Không tìm thấy trang</span>
      <h1>Đường dẫn này không còn tồn tại</h1>
      <p className="muted">Dữ liệu yêu cầu hoặc đơn đặt nấu trước đó không bị xóa. Bạn có thể trở lại đúng danh sách để tiếp tục.</p>
      <div className="row">
        <Link className="btn" to={ROUTES.MY_REQUESTS}>Yêu cầu của tôi</Link>
        <Link className="btn secondary" to={ROUTES.ORDERS}>Đơn đặt nấu</Link>
        <Link className="btn secondary" to={ROUTES.MENUS}>Thực đơn</Link>
      </div>
    </section>
  </main>;
}
