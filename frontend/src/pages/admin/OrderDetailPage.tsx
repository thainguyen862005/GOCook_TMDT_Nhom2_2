import {useCallback, useEffect, useState, type FormEvent, type ReactNode} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {adminClient} from '../../clients/adminClient';
import {adminOperationsClient} from '../../clients/adminOperationsClient';
import {Loading} from '../../components/common/Loading';
import {Notice} from '../../components/common/Notice';
import {ORDER_LABEL} from '../../constants/labels';
import {ROUTES} from '../../constants/routes';
import {AdminLayout} from '../../layouts/AdminLayout';
import type {AdminOrder} from '../../types/admin';
import type {UiMessage} from '../../types/api';
import {dateTime, money} from '../../util/format';

export function AdminOrderDetailPage() {
    const [params] = useSearchParams();
    const id = params.get('id');
    const [data, setData] = useState<{ order: AdminOrder; allowedStatuses: string[]; paymentState:string } | null>(null);
    const [status, setStatus] = useState('');
    const [note, setNote] = useState('');
    const [message, setMessage] = useState<UiMessage | null>(null);
    const [busy, setBusy] = useState(false);
    const [loading, setLoading] = useState(true);
    const [timeline, setTimeline] = useState<Record<string, unknown> | null>(null);

    const load = useCallback(async () => {
        setLoading(true);
        setData(null);
        setMessage(null);
        if (!id || !/^[1-9]\d*$/.test(id)) {
            setMessage({text: 'Mã đơn hàng không hợp lệ.'});
            setLoading(false);
            return;
        }
        try {
            const response = await adminClient.order(id);
            setData(response);
            setStatus(response.order.status);
            setNote(response.order.internalNote || '');
            void adminOperationsClient.orderTimeline(Number(id)).then(setTimeline).catch(() => setTimeline(null));
        } catch (error) {
            setMessage({text: error instanceof Error ? error.message : 'Không thể tải đơn hàng.'});
        } finally {
            setLoading(false);
        }
    }, [id]);

    useEffect(() => {
        void load();
    }, [load]);

    async function save(event: FormEvent) {
        event.preventDefault();
        if (!id) return;

        const currentStatus = data?.order.status;

        const criticalChange = currentStatus !== status && ['COMPLETED', 'CANCELLED', 'REJECTED'].includes(status);

        if (criticalChange) {
            const confirmed = window.confirm(`Xác nhận chuyển đơn từ "${ORDER_LABEL[currentStatus || ''] || currentStatus
                }" sang "${ORDER_LABEL[status] || status
                }"?`,
            );

            if (!confirmed) {
                return;
            }
        }

        setBusy(true);
        try {
            const response = await adminClient.updateOrder(id, {status, internalNote: note});
            setData(response);
            setStatus(response.order.status);
            setMessage({type: 'success', text: response.message || 'Đã cập nhật đơn hàng.'});
            void adminOperationsClient.orderTimeline(Number(id)).then(setTimeline).catch(() => setTimeline(null));
        } catch (error) {
            setMessage({text: error instanceof Error ? error.message : 'Không thể cập nhật đơn hàng.'});
        } finally {
            setBusy(false);
        }
    }

    if (loading) return <AdminLayout title="Chi tiết đơn hàng"><Loading/></AdminLayout>;
    if (!data) return <AdminLayout title="Chi tiết đơn hàng"><Notice message={message}/>
        <section className="card stack"><p>Không thể tải chi tiết đơn hàng.</p>
            <Link className="btn secondary" to={ROUTES.ADMIN_ORDERS}>Về danh sách</Link></section></AdminLayout>;
    const order = data.order;

    return (
        <AdminLayout title={`Đơn hàng ${order.orderCode}`} lead="Giám sát trạng thái thật, chỉ ghi chú nội bộ. Chef/Customer điều khiển vòng đời đơn; VNPay và escrow là nguồn xác nhận tiền.">
            <Notice message={message}/>
            <section className="card stack">
                <div className="grid grid-2">
                    <Info label="Khách hàng" value={order.customerName}/><Info label="Email"
                                                                               value={order.customerEmail || '—'}/>
                    <Info label="Thực đơn" value={order.productName}/><Info label="Loại dịch vụ" value={order.serviceType === 'BUDGET' ? 'Theo ngân sách' : 'Thực đơn'}/><Info label="Đầu bếp"
                                                                            value={order.chefName || '—'}/>
                    <Info label="Ngày nấu" value={order.scheduledAtDisplay || dateTime(order.scheduledAt)}/><Info
                    label="Số khách" value={order.guestCount}/>
                    <Info label="Tổng tiền" value={money(order.totalAmount)}/>
                    <Info label="Thanh toán" value={data.paymentState==='HELD'?'Đã tạm giữ':data.paymentState==='RELEASED'?'Đã giải ngân':data.paymentState==='VERIFYING_ESCROW'?'VNPay thành công, cần đối soát escrow':data.paymentState==='VNPAY_PENDING'?'Đang chờ VNPay':'Chưa thanh toán'}/><Info label="Địa chỉ"
                                                                                    value={order.serviceAddress || '—'}/>
                </div>
                {order.customerNote &&
                    <div><p className="small muted">Ghi chú khách hàng</p><p>{order.customerNote}</p></div>}
            </section>
            <section className="card stack-sm">
              <h3>Giao dịch & nhật ký Admin liên quan</h3>
              <p className="small muted">Nguồn payment gốc, không tự chỉnh status. Nhật ký dưới đây chỉ phản ánh các thao tác Admin đã ghi; chưa thay thế booking timeline của Thái.</p>
              {timeline ? <div className="grid grid-2">
                <div className="stack-sm"><h4>Giao dịch</h4>
                  {Array.isArray(timeline.payments) && timeline.payments.length > 0 ?
                    timeline.payments.map((item, index) => <pre className="soft admin-ops-json" key={index}>{JSON.stringify(item, null, 2)}</pre>) :
                    <p className="muted">Chưa có giao dịch.</p>}
                </div>
                <div className="stack-sm"><h4>Lịch sử Admin</h4>
                  {Array.isArray(timeline.adminHistory) && timeline.adminHistory.length > 0 ?
                    timeline.adminHistory.map((item, index) => <pre className="soft admin-ops-json" key={index}>{JSON.stringify(item, null, 2)}</pre>) :
                    <p className="muted">Chưa có lịch sử Admin.</p>}
                </div>
              </div> : <p className="muted">Chưa đọc được timeline quản trị (kiểm tra migration).</p>}
            </section>
            <form className="card stack" onSubmit={save}>
                <label className="field">Trạng thái (chỉ xem)<select disabled value={status}
                                                           onChange={(e) => setStatus(e.target.value)}>{(data.allowedStatuses || []).map((value) =>
                    <option key={value} value={value}>{ORDER_LABEL[value] || value}</option>)}</select></label>
                <label className="field">Ghi chú nội bộ<textarea maxLength={2000} value={note}
                                                                 onChange={(e) => setNote(e.target.value)}/></label>
                <div className="row">
                    <button className="btn" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu ghi chú nội bộ'}</button>
                    <Link className="btn secondary" to={ROUTES.ADMIN_ORDERS}>Quay lại</Link></div>
            </form>
        </AdminLayout>
    );
}

function Info({label, value}: { label: string; value: ReactNode }) {
    return <div><p className="small muted">{label}</p><strong>{value}</strong></div>;
}
