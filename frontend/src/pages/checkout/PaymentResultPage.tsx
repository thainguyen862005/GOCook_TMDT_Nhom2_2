import type { ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import { recallCheckoutLink } from '../../hooks/checkout/checkoutRetry';
import { usePaymentStatus } from '../../hooks/checkout/usePaymentStatus';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { PaymentStatusResult } from '../../types/payment';
import { dateTime, money } from '../../util/format';

export function PaymentResultPage() {
  useDocumentTitle('Kết quả thanh toán | GO Cook');
  const [params] = useSearchParams();
  const { view, result, error, recheck } = usePaymentStatus(params.get('vnp_TxnRef'));

  return (
    <>
      <div className="stepper">
        <span>
          <b>1</b>Chọn thực đơn
        </span>
        <span>
          <b>2</b>Giỏ hàng
        </span>
        <span>
          <b>3</b>Thanh toán
        </span>
        <span className="current">
          <b>4</b>Xác nhận
        </span>
      </div>
      <div className="container">
        <section className="card empty" aria-live="polite">
          {view === 'missing' && (
            <Panel
              icon={<path d="M12 8v5m0 3h.01M12 3 2 20h20z" />}
              heading="Không tìm thấy giao dịch"
              text="Đường dẫn này không kèm mã giao dịch. Hãy kiểm tra lại đơn hàng của bạn."
            >
              <Link className="btn" to={ROUTES.ORDERS}>
                Xem đơn hàng
              </Link>
            </Panel>
          )}

          {view === 'confirming' && (
            <Panel
              icon={<path d="M12 7v5l3 2M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z" />}
              heading="Đang xác nhận thanh toán"
              text="Vui lòng chờ trong giây lát, hệ thống đang nhận kết quả từ VNPay."
            />
          )}

          {view === 'waiting' && (
            <Panel
              icon={<path d="M12 7v5l3 2M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z" />}
              heading="Chưa nhận được kết quả từ VNPay"
              text="Nếu bạn đã thanh toán, kết quả có thể về chậm vài phút. Bạn có thể kiểm tra lại hoặc xem đơn hàng sau."
            >
              <div className="row">
                <button className="btn" type="button" onClick={recheck}>
                  Kiểm tra lại
                </button>
                <Link className="btn secondary" to={ROUTES.ORDERS}>
                  Xem đơn hàng
                </Link>
              </div>
            </Panel>
          )}

          {view === 'success' && result && (
            <Panel
              icon={<path d="m5 12 4 4L19 6" />}
              badge="Đã thanh toán"
              heading="Đặt lịch thành công"
              text={
                result.chefName
                  ? `${result.chefName} sẽ xác nhận và chuẩn bị bữa ăn cho bạn.`
                  : 'Đầu bếp sẽ xác nhận và chuẩn bị bữa ăn cho bạn.'
              }
            >
              <Summary result={result} paid />
              <div className="row">
                <Link className="btn" to={ROUTES.ORDERS}>
                  Xem đơn hàng
                </Link>
                <Link className="btn secondary" to={ROUTES.HOME}>
                  Về trang chủ
                </Link>
              </div>
            </Panel>
          )}

          {view === 'failed' && result && (
            <Panel
              icon={<path d="M6 6l12 12M18 6 6 18" />}
              heading={
                result.paymentStatus === 'CANCELLED'
                  ? 'Bạn đã hủy thanh toán'
                  : 'Thanh toán chưa thành công'
              }
              text="Đơn hàng này đã được hủy và bạn không bị trừ tiền. Hãy đặt lại để tiếp tục."
            >
              <Summary result={result} paid={false} />
              <div className="row">
                <Link
                  className="btn"
                  to={recallCheckoutLink(result.orderCode) ?? ROUTES.MENUS}
                >
                  Thử lại
                </Link>
                <Link className="btn secondary" to={ROUTES.HOME}>
                  Về trang chủ
                </Link>
              </div>
            </Panel>
          )}

          {view === 'error' && (
            <Panel
              icon={<path d="M12 8v5m0 3h.01M12 3 2 20h20z" />}
              heading="Không kiểm tra được kết quả"
              text={error ?? 'Không thể kết nối hệ thống. Vui lòng thử lại.'}
            >
              <div className="row">
                <button className="btn" type="button" onClick={recheck}>
                  Thử lại
                </button>
                <Link className="btn secondary" to={ROUTES.ORDERS}>
                  Xem đơn hàng
                </Link>
              </div>
            </Panel>
          )}
        </section>
      </div>
    </>
  );
}

function Panel({
  icon,
  badge,
  heading,
  text,
  children,
}: {
  icon: ReactNode;
  badge?: string;
  heading: string;
  text: string;
  children?: ReactNode;
}) {
  return (
    <>
      <span className="icon-box">
        <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
          {icon}
        </svg>
      </span>
      {badge && <span className="badge">{badge}</span>}
      <h1>{heading}</h1>
      <p className="muted">{text}</p>
      {children}
    </>
  );
}

function Summary({ result, paid }: { result: PaymentStatusResult; paid: boolean }) {
  return (
    <div className="soft">
      <strong>{result.productName}</strong>
      <p>
        {result.guestCount} người · {dateTime(result.scheduledAt)}
      </p>
      <p className={paid ? 'green' : 'muted'}>{money(result.amount)}</p>
      <p className="small muted">Mã đơn: {result.orderCode}</p>
    </div>
  );
}
