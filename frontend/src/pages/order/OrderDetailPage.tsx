import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { customerOrderClient } from '../../clients/customerOrderClient';
import { Loading } from '../../components/common/Loading';
import { Notice } from '../../components/common/Notice';
import { ORDER_LABEL } from '../../constants/labels';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { CustomerOrder } from '../../types/customerOrder';
import type { UiMessage } from '../../types/api';
import { dateTime, money } from '../../util/format';
import { statusClass } from '../../util/status';

export function OrderDetailPage() {
  useDocumentTitle('Chi tiết đơn đặt nấu | GO Cook');
  const [params] = useSearchParams();
  const rawId = params.get('id');
  const id = Number(rawId);
  const validId = rawId !== null && Number.isSafeInteger(id) && id > 0;
  const [order, setOrder] = useState<CustomerOrder | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<UiMessage | null>(null);
  const [busy, setBusy] = useState(false);
  async function accept() {
    if (!order || busy || order.status !== 'AWAITING_ACCEPTANCE') return;
    setBusy(true);setMessage(null);
    try {
      await customerOrderClient.accept(order.id);
      setOrder(await customerOrderClient.detail(order.id));
      setMessage({type:'success',text:'Đã nghiệm thu dịch vụ. Hệ thống ghi nhận giải ngân theo quy trình escrow.'});
    } catch(error) { setMessage({type:'error',text:error instanceof Error?error.message:'Không thể nghiệm thu.'}); }
    finally { setBusy(false); }
  }

  useEffect(() => {
    if (!validId) {
      setLoading(false);
      setOrder(null);
      setMessage({ text: 'Mã đơn hàng không hợp lệ.' });
      return;
    }
    let active = true;
    setLoading(true);
    setMessage(null);
    customerOrderClient.detail(id)
      .then((result) => { if (active) setOrder(result); })
      .catch((error: Error) => {
        if (active) { setOrder(null); setMessage({ text: error.message }); }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, validId]);

  return (
    <div className="container">
      <div className="page-heading">
        <nav className="breadcrumb" aria-label="Đường dẫn">
          <Link to={ROUTES.HOME}>Trang chủ</Link><span>/</span>
          <Link to={ROUTES.ORDERS}>Lịch sử mua hàng</Link><span>/</span>
          <span>Chi tiết đơn</span>
        </nav>
        <h1>{order ? `Đơn đặt nấu #${order.orderCode}` : 'Chi tiết đơn đặt nấu'}</h1>
      </div>
      <Notice message={message} />
      {loading ? <Loading /> : order ? (
        <div className="split">
          <section className="card stack">
            <div className="between">
              <h2>{order.productName}</h2>
              <span className={`badge ${statusClass(order.status)}`}>
                {ORDER_LABEL[order.status] || order.status}
              </span>
            </div>
            <p><strong>Mã đơn:</strong> {order.orderCode}</p>
            <p><strong>Đầu bếp:</strong> {order.chefName || 'Đang cập nhật'}</p>
            <p><strong>Số khách:</strong> {order.guestCount} người</p>
            <p><strong>Ngày đặt:</strong> {dateTime(order.createdAt)}</p>
            <p><strong>Lịch phục vụ:</strong> {dateTime(order.scheduledAt)}</p>
            <p><strong>Địa chỉ nấu:</strong> {order.serviceAddress || 'Chưa có thông tin'}</p>
            <p><strong>Ghi chú:</strong> {order.customerNote || 'Không có'}</p>
            {order.status === 'AWAITING_ACCEPTANCE' && <div className="soft stack"><strong>Đầu bếp đã báo hoàn thành.</strong><p>Chỉ xác nhận sau khi bạn kiểm tra dịch vụ. Hành động này sẽ giải ngân tiền đang tạm giữ cho Chef.</p><button className="btn" disabled={busy} onClick={() => void accept()}>{busy?'Đang xác nhận…':'Xác nhận nghiệm thu & giải ngân'}</button></div>}
            <Link className="btn secondary" to={ROUTES.ORDERS}>← Quay lại danh sách</Link>
          </section>
          <aside className="card summary stack">
            <h3>Chi phí đặt lịch</h3>
            <div className="summary-line"><span>Thực đơn</span><strong>{money(order.totalAmount)}</strong></div>
            <div className="summary-line total"><span>Tổng cộng</span><strong>{money(order.totalAmount)}</strong></div>
            <p className="small muted">Trang này hiển thị thông tin đơn hàng; không xác nhận đã thanh toán.</p>
          </aside>
        </div>
      ) : null}
    </div>
  );
}
