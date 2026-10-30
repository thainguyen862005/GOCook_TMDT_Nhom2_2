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
import { useRetryPayment } from '../../hooks/checkout/useRetryPayment';
import { OrderEvidenceGallery, type OrderEvidence } from '../../components/common/OrderEvidenceGallery';
import { OrderReviewForm } from '../../components/review/OrderReviewForm';

function PendingPayment({orderCode}: {orderCode:string}) {
  const {retry,retrying,error} = useRetryPayment(orderCode);
  return <div className="soft stack"><strong>Đơn chưa thanh toán</strong>
    <p>Đây mới là đơn giữ chỗ, Chef chưa được nhận nấu. Thanh toán qua VNPay để xác nhận.</p>
    <button className="btn" disabled={retrying} onClick={()=>void retry()}>{retrying?'Đang mở VNPay…':'Thanh toán qua VNPay'}</button>
    {error&&<p className="notice warm" role="alert">{error}</p>}
  </div>;
}

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
  const [reviewChecked, setReviewChecked] = useState(false);
  const [cancelReason,setCancelReason] = useState('');
  const [evidence, setEvidence] = useState<OrderEvidence[]>([]);
  const [evidenceError, setEvidenceError] = useState('');
  const [evidenceReload, setEvidenceReload] = useState(0);
  async function cancelOrder() {
    if (!order || busy || cancelReason.trim().length < 10 || !window.confirm('Xác nhận hủy đơn? Chính sách hoàn tiền tùy thời điểm.')) return;
    setBusy(true);setMessage(null);
    try {const result=await customerOrderClient.cancel(order.id,cancelReason.trim());
      setOrder(await customerOrderClient.detail(order.id));
      setMessage({type:'success',text:result.refundStatus==='NOT_REQUIRED'?'Đã hủy đơn, chưa thu tiền.':'Đã hủy đơn và tạo yêu cầu hoàn tiền. Chờ đối soát, chưa xác nhận đã chuyển tiền.'});
    } catch(e){setMessage({type:'error',text:e instanceof Error?e.message:'Không hủy được đơn.'});}
    finally {setBusy(false);}
  }
  async function accept() {
    if (!order || busy || order.status !== 'AWAITING_ACCEPTANCE') return;
    setBusy(true);setMessage(null);
    try {
      await customerOrderClient.accept(order.id);
      setOrder(await customerOrderClient.detail(order.id));
      setReviewChecked(false);
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
    setReviewChecked(false);
    customerOrderClient.detail(id)
      .then((result) => { if (active) setOrder(result); })
      .catch((error: Error) => {
        if (active) { setOrder(null); setMessage({ text: error.message }); }
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, validId]);

  useEffect(() => {
    if (!validId) { setEvidence([]); return; }
    const ctrl=new AbortController();
    setEvidenceError(''); setEvidence([]);
    void customerOrderClient.evidence(id,ctrl.signal).then(setEvidence).catch(error=>{
      if (!ctrl.signal.aborted) setEvidenceError(error instanceof Error?error.message:'Không xem được ảnh đơn hàng.');
    });
    return () => ctrl.abort();
  },[id,validId,evidenceReload]);
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
            {order.status === 'PENDING' && order.escrowStatus !== 'HELD' && <PendingPayment orderCode={order.orderCode} />}
            {order.status === 'PENDING' && order.escrowStatus === 'HELD' && <p className="notice">VNPay đã xác nhận và tiền đang được tạm giữ. Đang chờ Chef nhận đơn.</p>}
          <section className="stack quality-customer-evidence">
            <div className="between"><h2>Minh chứng từ đầu bếp</h2><button className="btn secondary sm" type="button" onClick={()=>setEvidenceReload(n=>n+1)}>Làm mới ảnh</button></div>
            <p className="small muted">Ảnh chỉ được chia sẻ với khách đặt đơn và đầu bếp phụ trách. Hóa đơn giúp đối chiếu nơi mua; GO Cook không tự động xác thực tính thật của hóa đơn.</p>
            {evidenceError&&<p role="alert" className="notice warm">{evidenceError}</p>}
            <OrderEvidenceGallery evidence={evidence} imagePath={evidenceId=>`/api/customer/orders/${order.id}/evidence/${evidenceId}/file`}/>
          </section>
            {order.status === 'AWAITING_ACCEPTANCE' && <div className="soft stack"><strong>Đầu bếp đã báo hoàn thành.</strong><p>Hãy xem ảnh món ở mục Minh chứng bên dưới và kiểm tra dịch vụ thực tế. Nghiệm thu sẽ giải ngân khoản tạm giữ.</p>
              {!evidence.some(item=>item.evidenceType==='FINISHED_MEAL')&&<p className="notice warm">Chưa thấy ảnh món hoàn thành. Đừng nghiệm thu nếu chưa đủ bằng chứng; hãy liên hệ đầu bếp hoặc hỗ trợ.</p>}
              <label className="row"><input type="checkbox" checked={reviewChecked} onChange={event=>setReviewChecked(event.target.checked)}/> Tôi đã kiểm tra hình ảnh và chất lượng phục vụ, đồng ý nghiệm thu.</label>
              <button className="btn" disabled={busy||!reviewChecked} onClick={() => void accept()}>{busy?'Đang xác nhận…':'Xác nhận nghiệm thu & giải ngân'}</button>
            </div>}
            {order.status==='COMPLETED' && <OrderReviewForm orderId={order.id} />}
            {['PENDING','CONFIRMED'].includes(order.status)&&<section className="soft stack">
              <h3>Hủy đơn hàng</h3><p className="small muted">Đơn đã thanh toán sẽ được tạo yêu cầu hoàn tiền theo chính sách. Không đồng nghĩa tiền đã về tài khoản.</p>
              <label className="field">Lý do hủy<textarea minLength={10} maxLength={500} value={cancelReason} onChange={e=>setCancelReason(e.target.value)} rows={2}/></label>
              <button className="btn secondary" disabled={busy||cancelReason.trim().length<10} onClick={()=>void cancelOrder()}>Yêu cầu hủy đơn</button>
            </section>}
            <Link className="btn" to={`${ROUTES.CHAT}?orderId=${order.id}`}>Nhắn tin với đầu bếp</Link>
            <Link className="btn secondary" to={ROUTES.ORDERS}>← Quay lại danh sách</Link>
          </section>
          <aside className="card summary stack">
            <h3>Chi phí đặt lịch</h3>
            <div className="summary-line"><span>Thực đơn</span><strong>{money(order.totalAmount)}</strong></div>
            <div className="summary-line total"><span>Tổng cộng</span><strong>{money(order.totalAmount)}</strong></div>
            <p className="small">Thanh toán: <strong>{order.escrowStatus==='HELD'?'Đã thanh toán, đang tạm giữ':order.escrowStatus==='RELEASED'?'Đã nghiệm thu':order.escrowStatus==='REFUND_PENDING'?'Đã yêu cầu hoàn tiền, chờ đối soát':order.escrowStatus==='REFUNDED'?'Đã hoàn tiền':'Chưa xác nhận thanh toán'}</strong></p>
            <p className="small muted">Trang này hiển thị thông tin đơn hàng; không xác nhận đã thanh toán.</p>
          </aside>

        </div>
      ) : null}
    </div>
  );
}
