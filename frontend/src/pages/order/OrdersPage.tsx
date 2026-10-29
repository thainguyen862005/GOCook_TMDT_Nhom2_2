import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { customerOrderClient } from '../../clients/customerOrderClient';
import { AccountSidebar } from '../../components/account/AccountSidebar';
import { Loading } from '../../components/common/Loading';
import { Notice } from '../../components/common/Notice';
import { Pager } from '../../components/common/Pager';
import { ORDER_LABEL } from '../../constants/labels';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { CustomerOrdersResponse } from '../../types/customerOrder';
import type { UiMessage } from '../../types/api';
import { dateTime, money } from '../../util/format';
import { statusClass } from '../../util/status';

const filters = [
  { value: '', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ xác nhận' },
  { value: 'CONFIRMED', label: 'Đã xác nhận' },
  { value: 'COOKING', label: 'Đang nấu' },
  { value: 'COMPLETED', label: 'Hoàn thành' },
  { value: 'CANCELLED', label: 'Đã hủy' },
];

export function OrdersPage() {
  useDocumentTitle('Lịch sử mua hàng | GO Cook');
  const [params, setParams] = useSearchParams();
  const status = params.get('status') || '';
  const queryPage = Number(params.get('page') || '0');
  const page = Number.isSafeInteger(queryPage) && queryPage >= 0 ? queryPage : 0;
  const [data, setData] = useState<CustomerOrdersResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<UiMessage | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setMessage(null);
    customerOrderClient.list({ status, page })
      .then((result) => { if (active) setData(result); })
      .catch((error: Error) => {
        if (active) { setData(null); setMessage({ text: error.message }); }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [status, page]);

  return (
    <div className="container">
      <div className="page-heading">
        <nav className="breadcrumb" aria-label="Đường dẫn">
          <Link to={ROUTES.HOME}>Trang chủ</Link><span>/</span><span>Lịch sử mua hàng</span>
        </nav>
        <h1>Lịch sử mua hàng</h1>
        <p className="lead">Xem lại những bữa ăn bạn đã đặt và theo dõi trạng thái đơn hàng.</p>
      </div>
      <div className="workspace">
        <AccountSidebar active="orders" />
        <div className="stack">
          <nav className="tabs" aria-label="Lọc trạng thái đơn hàng">
            {filters.map((filter) => (
              <Link
                key={filter.value}
                to={`${ROUTES.ORDERS}?${new URLSearchParams({ status: filter.value, page: '0' })}`}
                aria-current={status === filter.value ? 'page' : undefined}
              >{filter.label}</Link>
            ))}
          </nav>
          <Notice message={message} />
          {loading ? <Loading text="Đang tải lịch sử mua hàng..." /> : data?.content.length ? (
            <>
              <p className="small muted">Tìm thấy {data.totalElements} đơn hàng.</p>
              {data.content.map((order) => (
                <article className="card stack" key={order.id}>
                  <div className="between">
                    <strong>#{order.orderCode}</strong>
                    <span className={`badge ${statusClass(order.status)}`}>
                      {order.status==='PENDING'&&order.escrowStatus!=='HELD'?'Chờ thanh toán VNPay':ORDER_LABEL[order.status] || order.status}
                    </span>
                  </div>
                  <div>
                    <h3>{order.productName}</h3>
                    <p className="muted small">Đầu bếp: {order.chefName || 'Đang cập nhật'} · {order.guestCount} người</p>
                    <p className="muted small">Ngày đặt: {dateTime(order.createdAt)}</p>
                    <p className="muted small">Ngày phục vụ: {dateTime(order.scheduledAt)}</p>
                    <p className="muted small">Thanh toán: {order.escrowStatus==='HELD'?'Đã tạm giữ':order.escrowStatus==='RELEASED'?'Đã nghiệm thu':'Chưa xác nhận'}</p>
                  </div>
                  <div className="between">
                    <strong className="price">{money(order.totalAmount)}</strong>
                    <Link className="btn secondary sm" to={`${ROUTES.ORDER_DETAIL}?id=${order.id}`}>
                      {order.status==='PENDING'&&order.escrowStatus!=='HELD'?'Thanh toán / Chi tiết đơn':'Chi tiết đơn'}
                    </Link>
                  </div>
                </article>
              ))}
              <Pager data={data} onPage={(nextPage) => setParams({ status, page: String(nextPage) })} />
            </>
          ) : !message ? (
            <div className="card empty">
              <h3>Chưa có đơn hàng</h3>
              <p className="muted">Bạn chưa có đơn nào trong trạng thái này.</p>
              <Link className="btn" to={ROUTES.MENUS}>Xem thực đơn</Link>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
