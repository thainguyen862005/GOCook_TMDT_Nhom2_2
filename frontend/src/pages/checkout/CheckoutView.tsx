import { Link } from 'react-router-dom';
import { Loading } from '../../components/common/Loading';
import { Notice } from '../../components/common/Notice';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { useCheckout } from '../../hooks/checkout/useCheckout';
import { money } from '../../util/format';

export function CheckoutView({ title }: { title: string }) {
  useDocumentTitle(title);
  const checkout = useCheckout();
  const { product, form } = checkout;

  return (
    <>
      <div className="stepper">
        <span>
          <b>1</b>Chọn thực đơn
        </span>
        <span>
          <b>2</b>Giỏ hàng
        </span>
        <span className="current">
          <b>3</b>Thanh toán
        </span>
        <span>
          <b>4</b>Xác nhận
        </span>
      </div>
      <div className="container">
        <div className="page-heading">
          <nav className="breadcrumb" aria-label="Đường dẫn">
            <Link to={ROUTES.HOME}>Trang chủ</Link>
            <span>/</span>
            <span>Xác nhận lịch nấu</span>
          </nav>
          <h1>Xác nhận lịch nấu</h1>
          <p className="lead">Một bước nữa để bữa cơm nhà được chuẩn bị chu đáo.</p>
        </div>

        {!checkout.hasProductId && (
          <section className="card stack">
            <h3>Chưa chọn thực đơn</h3>
            <p className="muted">
              Không tìm thấy thực đơn cần đặt. Hãy chọn một thực đơn rồi quay lại bước thanh toán.
            </p>
            <Link className="btn" to={ROUTES.MENUS}>
              Xem thực đơn
            </Link>
          </section>
        )}

        {checkout.hasProductId && checkout.loading && <Loading text="Đang tải thực đơn..." />}

        {checkout.hasProductId && !checkout.loading && !product && (
          <section className="card stack">
            <Notice message={checkout.message} />
            <Link className="btn" to={ROUTES.MENUS}>
              Xem thực đơn khác
            </Link>
          </section>
        )}

        {product && (
          <div className="split">
            <form
              className="stack"
              onSubmit={(event) => {
                event.preventDefault();
                void checkout.submit();
              }}
            >
              <section className="card stack">
                <h3>Thực đơn đã chọn</h3>
                <div className="order-item">
                  {product.imageUrl && <img src={product.imageUrl} alt={product.name} loading="lazy" />}
                  <div>
                    <h3>{product.name}</h3>
                    <p className="small muted">
                      {[product.sellerName, `${money(Number(product.unitPrice))}/người`]
                        .filter(Boolean)
                        .join(' · ')}
                    </p>
                    <p className="small muted">
                      Phục vụ từ {product.minGuests} đến {product.maxGuests} người
                    </p>
                  </div>
                </div>
              </section>

              <section className="card stack">
                <h3>Địa chỉ & thời gian nấu</h3>
                <div className="grid grid-2">
                  <label className="field" htmlFor="customer">
                    Họ và tên
                    <input
                      id="customer"
                      name="customer"
                      type="text"
                      value={form.customerName}
                      onChange={(event) => form.setCustomerName(event.target.value)}
                      maxLength={255}
                      required
                    />
                  </label>
                  <label className="field" htmlFor="phone">
                    Số điện thoại
                    <input
                      id="phone"
                      name="phone"
                      type="tel"
                      value={form.customerPhone}
                      onChange={(event) => form.setCustomerPhone(event.target.value)}
                      placeholder="Nhập số điện thoại"
                      autoComplete="tel"
                      pattern="[0-9+\-\s]{8,30}"
                      required
                    />
                  </label>
                </div>
                <label className="field" htmlFor="address">
                  Địa chỉ nấu ăn
                  <input
                    id="address"
                    name="address"
                    type="text"
                    value={form.serviceAddress}
                    onChange={(event) => form.setServiceAddress(event.target.value)}
                    placeholder="Số nhà, đường, phường, thành phố"
                    autoComplete="street-address"
                    maxLength={1000}
                    required
                  />
                </label>
                <div className="grid grid-2">
                  <label className="field" htmlFor="guests">
                    Số người ({product.minGuests}–{product.maxGuests})
                    <input
                      id="guests"
                      name="guests"
                      type="number"
                      min={product.minGuests}
                      max={product.maxGuests}
                      value={checkout.guestCount}
                      onChange={(event) => form.setGuests(Number(event.target.value))}
                      required
                    />
                  </label>
                  <label className="field" htmlFor="order-date">
                    Ngày dùng bữa
                    <input
                      id="order-date"
                      name="order-date"
                      type="date"
                      value={form.date}
                      onChange={(event) => form.setDate(event.target.value)}
                      required
                    />
                  </label>
                </div>
                <label className="field" htmlFor="order-time">
                  Giờ dùng bữa
                  <select
                    id="order-time"
                    name="order-time"
                    value={form.time}
                    onChange={(event) => form.setTime(event.target.value)}
                  >
                    {checkout.times.map((slot) => (
                      <option key={slot} value={slot}>
                        {slot}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field" htmlFor="allergies">
                  Khẩu vị & dị ứng thực phẩm
                  <textarea
                    id="allergies"
                    name="allergies"
                    value={form.customerNote}
                    onChange={(event) => form.setCustomerNote(event.target.value)}
                    maxLength={2000}
                  />
                </label>
              </section>

              <section className="card stack">
                <h3>Phương thức thanh toán</h3>
                <label className="payment-option">
                  <input type="radio" name="payment" defaultChecked />
                  <span>
                    <strong>VNPay</strong>
                    <span className="small muted"> · ATM / Visa / Mastercard / QR</span>
                  </span>
                </label>
              </section>
            </form>

            <aside className="card summary stack">
              <h3>Tóm tắt đặt lịch</h3>
              <div>
                <div className="summary-line">
                  <span>Thực đơn · {checkout.guestCount} người</span>
                  <strong>{money(checkout.total)}</strong>
                </div>
                <div className="summary-line">
                  <span>Phí nguyên liệu & công nấu</span>
                  <span className="green">Đã bao gồm</span>
                </div>
                <div className="summary-line">
                  <span>Dọn dẹp gian bếp</span>
                  <span className="green">Đã bao gồm</span>
                </div>
                <div className="summary-line">
                  <span>Ưu đãi</span>
                  <span>0đ</span>
                </div>
                <div className="summary-line total">
                  <span>Tổng cộng</span>
                  <strong>{money(checkout.total)}</strong>
                </div>
              </div>
              <label className="field" htmlFor="voucher">
                Mã ưu đãi
                <input id="voucher" name="voucher" type="text" placeholder="Nhập mã ưu đãi" disabled />
              </label>
              <button
                className="btn secondary"
                type="button"
                disabled
                title="Chức năng sẽ được tích hợp ở giai đoạn tiếp theo"
              >
                Áp dụng
              </button>
              <label className="check">
                <input
                  type="checkbox"
                  name="agree"
                  checked={form.agreed}
                  onChange={(event) => form.setAgreed(event.target.checked)}
                />
                <span>Tôi đã xem thông tin đặt lịch và chính sách dịch vụ.</span>
              </label>
              <Notice message={checkout.message} />
              {!checkout.guestsInRange && (
                <p className="small muted">
                  Số người phải từ {product.minGuests} đến {product.maxGuests}.
                </p>
              )}
              <button
                className="btn"
                type="button"
                disabled={!checkout.canSubmit}
                onClick={() => void checkout.submit()}
              >
                {checkout.submitting
                  ? 'Đang chuyển sang VNPay...'
                  : `Thanh toán ${money(checkout.total)}`}
              </button>
              <p className="small muted">
                Bạn sẽ được chuyển sang cổng VNPay để hoàn tất thanh toán an toàn.
              </p>
            </aside>
          </div>
        )}
      </div>
    </>
  );
}
