import { useState } from 'react';
import { SellerWorkspace } from '../../components/common/SellerWorkspace';
import { useSellerFinance } from '../../hooks/seller/useSellerFinance';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { dateTime, money } from '../../util/format';

function monthLabel(month: string): string {
  return `${month.slice(5)}/${month.slice(0, 4)}`;
}

function toDay(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function SellerRevenuePage() {
  useDocumentTitle('Doanh thu | GO Cook');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const reversed = from !== '' && to !== '' && from > to;
  const finance = useSellerFinance(reversed ? '' : from, reversed ? '' : to);
  const { summary, monthly, transactions } = finance;

  function thisMonth() {
    const now = new Date();
    setFrom(toDay(new Date(now.getFullYear(), now.getMonth(), 1)));
    setTo(toDay(new Date(now.getFullYear(), now.getMonth() + 1, 0)));
    finance.resetPage();
  }

  const months = monthly.data ?? [];
  const maxIncome = Math.max(1, ...months.map((month) => month.earnedAmount));
  const mix = summary.data;
  const totalOrders = mix ? mix.menuOrderCount + mix.budgetOrderCount : 0;
  const menuPercent = mix && totalOrders ? Math.round((mix.menuOrderCount / totalOrders) * 100) : 0;

  return (
    <SellerWorkspace
      title="Doanh thu"
      description="Rõ từng khoản tạm giữ, đúng phần thu nhập bạn được hưởng."
    >
      <section className="card stack-sm">
        <div className="chef-filters">
          <label className="field">
            Từ ngày
            <input
              type="date"
              value={from}
              onChange={(event) => {
                setFrom(event.target.value);
                finance.resetPage();
              }}
            />
          </label>
          <label className="field">
            Đến ngày
            <input
              type="date"
              value={to}
              onChange={(event) => {
                setTo(event.target.value);
                finance.resetPage();
              }}
            />
          </label>
          <div className="field">
            <span>Chọn nhanh</span>
            <button className="btn secondary" type="button" onClick={thisMonth}>
              Tháng này
            </button>
          </div>
          <div className="field">
            <span>&nbsp;</span>
            <button
              className="btn secondary"
              type="button"
              onClick={() => {
                setFrom('');
                setTo('');
                finance.resetPage();
              }}
            >
              Tất cả
            </button>
          </div>
        </div>
        {reversed && (
          <p className="danger" role="alert">
            Từ ngày không được sau đến ngày.
          </p>
        )}
        <p className="small muted">
          Thu nhập: lọc theo ngày khách nghiệm thu. Tiền đang tạm giữ: lọc theo ngày nhận tiền. Múi
          giờ Việt Nam. Khoản đã hoàn tiền hoặc đang chờ hoàn không được tính.
        </p>
      </section>

      {summary.error && (
        <p className="danger" role="alert">
          {summary.error}
        </p>
      )}
      {summary.loading && !summary.data && <p className="muted">Đang tải doanh thu...</p>}
      {summary.data && (
        <div className="grid grid-3 chef-revenue-metrics">
          {[
            ['Thu nhập được hưởng', summary.data.availableAmount, `Từ ${summary.data.releasedOrderCount} đơn khách đã nghiệm thu, sau phí nền tảng.`],
            ['Tiền đang tạm giữ', summary.data.pendingAmount, 'Phần của bạn trong các đơn đã thanh toán nhưng chưa nghiệm thu.'],
            ['Phí nền tảng', summary.data.platformFeeTotal, 'Phí của các đơn đã nghiệm thu trong kỳ.'],
          ].map(([title, value, hint]) => (
            <article className="card chef-metric" key={String(title)}>
              <p className="small muted">{title}</p>
              <strong className="chef-metric-value">{money(Number(value))}</strong>
              <p className="small muted">{hint}</p>
            </article>
          ))}
        </div>
      )}

      <section className="card stack">
        <div>
          <p className="eyebrow">Thu nhập sau nghiệm thu</p>
          <h2>Thu nhập Chef theo tháng</h2>
          <p className="small muted">6 tháng gần nhất · Tháng không có thu nhập hiển thị 0đ</p>
        </div>
        {monthly.error && (
          <p className="danger" role="alert">
            {monthly.error}
          </p>
        )}
        {monthly.loading && !monthly.data && <p className="muted">Đang tải biểu đồ...</p>}
        {monthly.data && (
          <div className="chef-chart-scroll">
            <div
              className="chef-bars"
              role="img"
              aria-label={`Thu nhập theo tháng: ${months.map((month) => `${monthLabel(month.month)}: ${money(month.earnedAmount)}`).join('; ')}`}
            >
              {months.map((month) => (
                <div className="chef-bar-column" key={month.month}>
                  <strong className="small">{money(month.earnedAmount)}</strong>
                  <div className="chef-bar-track">
                    <div
                      className="chef-bar-fill"
                      style={{ height: `${(month.earnedAmount / maxIncome) * 100}%` }}
                    />
                  </div>
                  <span className="small muted">{monthLabel(month.month)}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </section>

      <section className="card stack">
        <div>
          <p className="eyebrow">Cơ cấu đơn đã thanh toán</p>
          <h2>Thực đơn có sẵn & theo ngân sách</h2>
          <p className="small muted">
            Theo số đơn đang tạm giữ hoặc đã nghiệm thu, tính theo ngày nhận tiền; không gồm đơn đã
            hoàn tiền.
          </p>
        </div>
        <div className="chef-pie-layout">
          <div
            className="chef-pie"
            role="img"
            aria-label={
              totalOrders
                ? `MENU ${mix?.menuOrderCount} đơn, BUDGET ${mix?.budgetOrderCount} đơn`
                : 'Chưa có đơn đã thanh toán trong kỳ'
            }
            style={{
              background: totalOrders
                ? `conic-gradient(var(--brand) 0 ${menuPercent}%, #d6b773 ${menuPercent}% 100%)`
                : 'var(--line)',
            }}
          >
            <div>
              <strong>{totalOrders}</strong>
              <span className="small muted">đơn đã thanh toán</span>
            </div>
          </div>
          <div className="stack">
            <div className="chef-legend">
              <span className="chef-dot" />
              <div>
                <strong>Thực đơn có sẵn · MENU</strong>
                <p className="muted">
                  {mix?.menuOrderCount ?? 0} đơn · {menuPercent}%
                </p>
              </div>
            </div>
            <div className="chef-legend">
              <span className="chef-dot budget" />
              <div>
                <strong>Theo ngân sách · BUDGET</strong>
                <p className="muted">
                  {mix?.budgetOrderCount ?? 0} đơn · {totalOrders ? 100 - menuPercent : 0}%
                </p>
              </div>
            </div>
            {!totalOrders && <p className="muted">Chưa có dữ liệu trong kỳ.</p>}
          </div>
        </div>
      </section>

      <section className="card stack">
        <div>
          <p className="eyebrow">Chi tiết</p>
          <h2>Từng khoản tiền</h2>
        </div>
        {transactions.error && (
          <p className="danger" role="alert">
            {transactions.error}
          </p>
        )}
        {transactions.loading && !transactions.data && <p className="muted">Đang tải danh sách...</p>}
        {transactions.data && transactions.data.content.length === 0 && (
          <p className="muted">Chưa có khoản nào trong khoảng ngày đã chọn.</p>
        )}
        {transactions.data && transactions.data.content.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Mã đơn</th>
                  <th>Giá trị đơn</th>
                  <th>Phí nền tảng</th>
                  <th>Bạn nhận</th>
                  <th>Trạng thái</th>
                  <th>Thời gian</th>
                </tr>
              </thead>
              <tbody>
                {transactions.data.content.map((row) => (
                  <tr key={row.orderCode}>
                    <td>{row.orderCode}</td>
                    <td>{money(row.grossAmount)}</td>
                    <td>
                      {money(row.platformFee)} ({row.platformFeePercent}%)
                    </td>
                    <td>{money(row.chefAmount)}</td>
                    <td>{row.status === 'RELEASED' ? 'Đã nghiệm thu' : 'Đang tạm giữ'}</td>
                    <td>{dateTime(row.releasedAt ?? row.heldAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {transactions.data && transactions.data.totalPages > 1 && (
          <div className="row">
            <button
              className="btn secondary"
              type="button"
              disabled={finance.page === 0 || transactions.loading}
              onClick={() => finance.goToPage(finance.page - 1)}
            >
              Trang trước
            </button>
            <span className="small muted">
              Trang {finance.page + 1}/{transactions.data.totalPages}
            </span>
            <button
              className="btn secondary"
              type="button"
              disabled={finance.page + 1 >= transactions.data.totalPages || transactions.loading}
              onClick={() => finance.goToPage(finance.page + 1)}
            >
              Trang sau
            </button>
          </div>
        )}
      </section>
    </SellerWorkspace>
  );
}
