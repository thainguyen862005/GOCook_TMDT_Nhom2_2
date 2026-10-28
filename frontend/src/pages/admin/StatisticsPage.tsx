import {useCallback, useEffect, useMemo, useState, type FormEvent,} from 'react';

import {adminClient} from '../../clients/adminClient';
import {appConfig} from '../../config/appConfig';
import {Loading} from '../../components/common/Loading';
import {MetricCard} from '../../components/common/MetricCard';
import {Notice} from '../../components/common/Notice';
import {AdminLayout} from '../../layouts/AdminLayout';

import type {RevenueGroupBy, StatisticsResponse,} from '../../types/admin';
import type {UiMessage} from '../../types/api';
import {money} from '../../util/format';

function isoDate(date: Date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');

    return `${year}-${month}-${day}`;
}

function rangeFor(groupBy: RevenueGroupBy,): { from: string; to: string; } {
    const today = new Date();
    const to = isoDate(today);

    if (groupBy === 'DAY') {
        const from = new Date(today);
        from.setDate(from.getDate() - 29,);

        return {from: isoDate(from), to,};
    }

    if (groupBy === 'YEAR') {
        return {from: `${today.getFullYear() - 4}-01-01`, to,};
    }

    return {from: isoDate(new Date(today.getFullYear(), today.getMonth() - 5, 1,),), to,};
}

export function AdminStatisticsPage() {

    const initial = rangeFor('MONTH');
    const [from, setFrom] = useState(initial.from);
    const [to, setTo] = useState(initial.to);
    const [groupBy, setGroupBy] = useState<RevenueGroupBy>('MONTH',);
    const [data, setData] = useState<StatisticsResponse | null>(null,);
    const [message, setMessage] = useState<UiMessage | null>(null,);

    const load =
        useCallback(async () => {
            setMessage(null);

            try {
                setData(await adminClient.statistics(from, to, groupBy,),);
            } catch (error) {
                setMessage({text: error instanceof Error ? error.message : 'Không thể tải thống kê.',});
            }
        }, [from, to, groupBy]);

    useEffect(() => {
        void load();
    }, []);

    const chart =
        useMemo(() => {

            if (!data) {
                return {
                    max: 1,
                    totalStatus: 1,
                    completed: 0,
                    cancelled: 0,
                    active: 0,
                    rejected: 0,
                };
            }

            const max =
                Math.max(
                    1,
                    ...data.revenueSeries.map(
                        (item) =>
                            Number(
                                item.revenue || 0,
                            ),
                    ),
                );

            const totalStatus = Object.values(data.statusCounts || {},).reduce((sum, value) => sum + Number(value), 0,) || 1;
            const completed = Number(data.statusCounts?.COMPLETED || 0,);
            const cancelled = Number(data.statusCounts?.CANCELLED || 0,);
            const rejected = Number(data.statusCounts?.REJECTED || 0,);

            return {
                max,
                totalStatus,
                completed,
                cancelled,
                active: Math.max(0, totalStatus - cancelled - rejected,),
                rejected,
            };
        }, [data]);

    function submit(event: FormEvent) {
        event.preventDefault();

        if (!from || !to || from > to) {
            setMessage({
                text: 'Khoảng thời gian thống kê không hợp lệ.',
            });
            return;
        }

        void load();
    }

    function changeGrouping(next: RevenueGroupBy,) {
        const range = rangeFor(next);
        setGroupBy(next);
        setFrom(range.from);
        setTo(range.to);
    }

    return (
        <AdminLayout title="Thống kê & doanh thu"
                     lead="Phân tích doanh thu theo ngày, tháng hoặc năm từ các đơn đã hoàn thành.">

            <Notice message={message}/>

            <form className="card stack-sm admin-statistics-filter" onSubmit={submit}>
                <div className="grid grid-4">
                    <label className="field">
                        Nhóm doanh thu

                        <select
                            value={groupBy}
                            onChange={(e) => changeGrouping(e.target.value as RevenueGroupBy,)}
                        >
                            <option value="DAY">Theo ngày</option>
                            <option value="MONTH">Theo tháng</option>
                            <option value="YEAR">Theo năm</option>
                        </select>

                    </label>

                    <label className="field">
                        Từ ngày
                        <input
                            type="date"
                            value={from}
                            max={to || undefined}
                            onChange={(event) => setFrom(event.target.value)}
                        />
                    </label>

                    <label className="field">
                        Đến ngày
                        <input
                            type="date"
                            value={to}
                            min={from || undefined}
                            onChange={(event) => setTo(event.target.value)}
                        />
                    </label>

                    <div className="field">
                        <span>&nbsp;</span>
                        <button className="btn">Áp dụng</button>
                    </div>

                </div>

                <div className="row">
                    <a className="btn secondary sm" href={`${appConfig.apiBaseUrl}/api/admin/reports/csv?type=bookings&from=${from}&to=${to}`} download>Xuất CSV đơn hàng</a>
                    <a className="btn secondary sm" href={`${appConfig.apiBaseUrl}/api/admin/reports/csv?type=payments&from=${from}&to=${to}`} download>Xuất CSV giao dịch</a>
                    <button className="btn secondary sm" type="button" onClick={() => changeGrouping('DAY')}>30 ngày
                    </button>
                    <button className="btn secondary sm" type="button" onClick={() => changeGrouping('MONTH')}>6 tháng
                    </button>
                    <button className="btn secondary sm" type="button" onClick={() => changeGrouping('YEAR')}>5 năm
                    </button>
                </div>

            </form>
            {!data ? <Loading/> : (
                <>
                    <div className="grid grid-4 admin-stats-metrics">
                        <MetricCard title="Doanh thu hoàn thành" value={money(data.revenue)}/>
                        <MetricCard title="Tổng đơn trong kỳ" value={data.orderCount}/>
                        <MetricCard title="Đầu bếp hoạt động" value={data.chefCount}/>
                        <MetricCard title="Người dùng" value={data.userCount}/>
                    </div>

                    <div className="grid grid-2 admin-stats-charts">
                        <figure className="card stack-sm">
                            <figcaption>
                                <h3>Biểu đồ doanh thu</h3>
                                <p className="small muted">Chỉ tính đơn có trạng thái Hoàn thành</p>
                            </figcaption>

                            <div className="admin-chart-scroll" role="region" aria-label="Biểu đồ doanh thu, cuộn ngang để xem tất cả kỳ" tabIndex={0}>
                                <div className="chart admin-revenue-chart">
                                    {data.revenueSeries.map(
                                        (item) => (
                                            <div
                                                className="chart-col"
                                                key={item.key}
                                                title={`${item.label}: ` + `${money(item.revenue)} · ` + `${item.completedOrders} đơn`}
                                            >

                                                <span>{Number(item.revenue,) > 0 ? `${(Number(item.revenue,) / 1_000_000).toFixed(1)}tr` : '0'}</span>

                                                <div className="bar" style={{
                                                    height: `${Number(item.revenue,) === 0 ? 2 : Math.max(6, Number(item.revenue,) / chart.max * 100,)}%`,
                                                }}
                                                />
                                                <span>{item.label}</span>
                                            </div>
                                        ),
                                    )}
                                </div>
                            </div>
                        </figure>

                        <figure className="card">
                            <figcaption>
                                <h3>Cơ cấu trạng thái đơn</h3>
                                <p className="small muted">Tỷ trọng đơn trong khoảng thời gian đã chọn</p>
                            </figcaption>

                            <div
                                className="donut"
                                style={{
                                    background:
                                        `conic-gradient(
                        var(--green, #2f6b12)
                        0
                        ${
                                            chart.active
                                            / chart.totalStatus
                                            * 100
                                        }%,
                        #c88b45
                        ${
                                            chart.active
                                            / chart.totalStatus
                                            * 100
                                        }%
                        100%
                      )`,
                                }}
                            >

                                <div>
                                    <strong>{data.orderCount}</strong>
                                    <small>Đơn đặt nấu</small>
                                </div>
                            </div>

                            <div className="summary-line">
                                <span>Không hủy</span>
                                <strong>{Math.round(chart.active / chart.totalStatus * 100,)}%</strong>
                            </div>

                            <div className="summary-line">
                                <span>Đã hủy</span>
                                <strong>{Math.round(chart.cancelled / chart.totalStatus * 100,)}%</strong>
                            </div>

                            <div className="summary-line">
                                <span>Đã từ chối</span>
                                <strong>{Math.round(chart.rejected / chart.totalStatus * 100,)}%</strong>
                            </div>

                            <div className="summary-line">
                                <span>Hoàn thành</span>
                                <strong>{Math.round(chart.completed / chart.totalStatus * 100,)}%</strong>
                            </div>
                        </figure>
                    </div>

                    <section className="card stack-sm">
                        <div className="between">
                            <h3>Chi tiết doanh thu</h3>
                            <span className="small muted">{data.from} → {data.to}</span>
                        </div>

                        <div className="table-wrap admin-table-scroll" role="region" aria-label="Bảng thống kê doanh thu" tabIndex={0}>
                            <table className="admin-statistics-table">
                                <thead>
                                <tr>
                                    <th>Kỳ</th>
                                    <th>Đơn hoàn thành</th>
                                    <th>Doanh thu</th>
                                </tr>
                                </thead>

                                <tbody>
                                {data.revenueSeries.map((item) => (
                                        <tr key={item.key}>
                                            <td><strong>{item.label}</strong></td>
                                            <td>{item.completedOrders}</td>
                                            <td>{money(item.revenue)}</td>
                                        </tr>
                                    ),
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