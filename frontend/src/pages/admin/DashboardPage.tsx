import {useCallback, useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {adminClient} from '../../clients/adminClient';
import {adminOperationsClient} from '../../clients/adminOperationsClient';
import {Loading} from '../../components/common/Loading';
import {MetricCard} from '../../components/common/MetricCard';
import {Notice} from '../../components/common/Notice';
import {ORDER_LABEL} from '../../constants/labels';
import {ROUTES} from '../../constants/routes';
import {AdminLayout} from '../../layouts/AdminLayout';
import type {DashboardResponse} from '../../types/admin';
import type {UiMessage} from '../../types/api';
import {dateTime, money} from '../../util/format';
import {statusClass} from '../../util/status';

export function AdminDashboardPage() {
    const [data, setData] = useState<DashboardResponse | null>(null);
    const [message, setMessage] = useState<UiMessage | null>(null);
    const [operations, setOperations] = useState<Array<{metric: string; value: number}>>([]);

    const load = useCallback(async () => {
        setMessage(null);

        try {
            setData(await adminClient.dashboard());
            // Operations counters fail independently if a migration has not yet been applied.
            void adminOperationsClient.summary().then(setOperations).catch(() => setOperations([]));
        } catch (error) {
            setMessage({
                text: error instanceof Error
                    ? error.message
                    : 'Không thể tải tổng quan.',
            });
        }
    }, []);

    useEffect(() => {
        void load();
    }, [load]);

    return (
        <AdminLayout
            title="Tổng quan"
            lead="Theo dõi nhanh hoạt động của GO Cook từ dữ liệu thật trong hệ thống."
        >
            <Notice message={message}/>

            {!data ? (
                <Loading/>
            ) : (
                <>
                    <section className="stack-sm">
                        <div className="between">
                            <div>
                                <p className="eyebrow">Vận hành</p>
                                <h3>Tổng quan hệ thống</h3>
                            </div>

                            <div className="row">
                                <button
                                    className="btn secondary sm"
                                    type="button"
                                    onClick={() => void load()}
                                >
                                    Làm mới
                                </button>

                                <Link
                                    className="text-link"
                                    to={ROUTES.ADMIN_STATISTICS}
                                >
                                    Xem báo cáo chi tiết →
                                </Link>
                            </div>
                        </div>

                        <div className="grid grid-4">
                            <MetricCard
                                title="Người dùng"
                                value={data.userCount}
                            />

                            <MetricCard
                                title="Đầu bếp hoạt động"
                                value={data.activeChefCount}
                            />

                            <MetricCard
                                title="Sản phẩm đang hiển thị"
                                value={data.productCount}
                            />

                            <MetricCard
                                title="Tổng đơn hàng"
                                value={data.orderCount}
                            />
                        </div>
                    </section>

                    <section className="stack-sm">
                        <div>
                            <p className="eyebrow">Doanh thu</p>
                            <h3>Doanh thu đơn đã hoàn thành</h3>
                        </div>

                        <div className="grid grid-4">
                            <MetricCard
                                title="Hôm nay"
                                value={money(data.todayRevenue)}
                            />

                            <MetricCard
                                title="Tháng này"
                                value={money(data.thisMonthRevenue)}
                            />

                            <MetricCard
                                title="Năm nay"
                                value={money(data.thisYearRevenue)}
                            />

                            <MetricCard
                                title="Toàn thời gian"
                                value={money(data.completedRevenue)}
                            />
                        </div>
                    </section>

                    <section className="stack-sm">
                        <div><p className="eyebrow">Trung tâm điều hành</p><h3>Công việc cần xử lý</h3></div>
                        {operations.length ? <div className="grid grid-4">
                            {operations.filter((item) => item.metric !== 'successful_payment_amount').map((item) =>
                                <MetricCard key={item.metric} title={{
                                    pending_verifications: 'Chef chờ xác minh',
                                    open_cases: 'Khiếu nại chưa đóng',
                                    pending_refunds: 'Yêu cầu hoàn tiền',
                                    open_tickets: 'Ticket chưa đóng',
                                    pending_review_reports: 'Đánh giá bị báo cáo',
                                }[item.metric] || item.metric} value={item.value} />)}
                        </div> : <p className="muted small">Vui lòng chạy migration Admin Operations để xem tác vụ đang chờ.</p>}
                        <p className="small muted">Giá trị đơn hoàn thành được tính theo lịch nấu; tổng thanh toán thành công từ gateway là KPI riêng và không đồng nghĩa lợi nhuận.</p>
                        {operations.some((item) => item.metric === 'successful_payment_amount') &&
                          <MetricCard title="Tổng giao dịch SUCCESS (gross, chưa trừ refund)" value={money(
                            operations.find((item) => item.metric === 'successful_payment_amount')?.value || 0
                          )} />}
                    </section>

                    <section className="card stack">
                        <div className="between">
                            <h3>Đơn hàng gần đây</h3>

                            <Link
                                className="text-link"
                                to={ROUTES.ADMIN_ORDERS}
                            >
                                Xem tất cả
                            </Link>
                        </div>

                        <div className="table-wrap">
                            <table>
                                <thead>
                                <tr>
                                    <th>Mã đơn</th>
                                    <th>Khách hàng</th>
                                    <th>Thực đơn</th>
                                    <th>Ngày nấu</th>
                                    <th>Trạng thái</th>
                                </tr>
                                </thead>

                                <tbody>
                                {(data.recentOrders ?? []).length === 0 ? (
                                    <tr>
                                        <td colSpan={5} className="center muted">
                                            Chưa có đơn hàng gần đây.
                                        </td>
                                    </tr>
                                ) : (
                                    data.recentOrders.map((order) => (
                                        <tr key={order.id}>
                                            <td>
                                                <Link
                                                    className="text-link"
                                                    to={`${ROUTES.ADMIN_ORDER_DETAIL}?id=${order.id}`}
                                                >
                                                    {order.orderCode}
                                                </Link>
                                            </td>

                                            <td>{order.customerName}</td>

                                            <td>{order.productName}</td>

                                            <td>
                                                {order.scheduledAtDisplay
                                                    || dateTime(order.scheduledAt)}
                                            </td>

                                            <td>
                          <span
                              className={`badge ${statusClass(order.status)}`}
                          >
                            {ORDER_LABEL[order.status] || order.status}
                          </span>
                                            </td>
                                        </tr>
                                    ))
                                )}
                                </tbody>
                            </table>
                        </div>
                    </section>
                </>
            )}
        </AdminLayout>
    );
}