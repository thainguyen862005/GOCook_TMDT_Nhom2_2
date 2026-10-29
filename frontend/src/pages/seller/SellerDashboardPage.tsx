import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { chefOrderClient } from '../../clients/chefOrderClient';
import { workflowClient,type WorkflowRequest } from '../../clients/workflowClient';
import { sellerScheduleClient } from '../../clients/sellerScheduleClient';
import { SellerWorkspace } from '../../components/common/SellerWorkspace';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { ChefOrderSummary } from '../../types/chefOrder';
import { chefStatusLabels } from '../../types/chefOrder';
import { dateTime, money } from '../../util/format';

const day=(date:Date)=>`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
export function SellerDashboardPage(){
  useDocumentTitle('Tổng quan đầu bếp | GO Cook');
  const [data,setData]=useState<ChefOrderSummary|null>(null);
  const [slots,setSlots]=useState<{date:string; bookings:number; available:boolean}[]>([]);
  const [error,setError]=useState('');
  const [loading,setLoading]=useState(true);
  const [newRequests,setNewRequests]=useState<WorkflowRequest[]>([]);
  const [requestsError,setRequestsError]=useState('');
  const [reload,setReload]=useState(0);
  useEffect(()=>{
    const controller=new AbortController();setLoading(true);setError('');
    const now=new Date();const dates=Array.from({length:7},(_,i)=>{const d=new Date(now);d.setDate(now.getDate()+i);return day(d);});
    Promise.all([chefOrderClient.summary(controller.signal),sellerScheduleClient.list(dates[0],dates[6],controller.signal)])
      .then(([summary,schedule])=>{if(!controller.signal.aborted){setData(summary);setSlots(dates.map(date=>({date,
        bookings:schedule.bookings.filter(b=>b.startsAt.slice(0,10)<=date&&b.endsAt.slice(0,10)>=date).length,
        available:schedule.slots.some(s=>s.serviceDate===date&&s.available)})));}})
      .catch(e=>{if(!controller.signal.aborted)setError(e instanceof Error?e.message:'Không tải được dashboard.');})
      .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
    workflowClient.chefRequests()
      .then(rows=>{if(!controller.signal.aborted){setNewRequests(rows.filter(row=>row.status!=='CLOSED'&&!row.myPrice));setRequestsError('');}})
      .catch(err=>{if(!controller.signal.aborted)setRequestsError(err instanceof Error?err.message:'Không tải được yêu cầu.');});
    return ()=>controller.abort();
  },[reload]);
  return <SellerWorkspace title="Tổng quan đầu bếp" description="Đơn hàng, tiến độ và lịch phục vụ được lấy từ dữ liệu thực tế.">
    <div className="between"><p className="small muted">Số liệu cập nhật khi tải trang hoặc nhấn làm mới.</p><button className="btn secondary sm" onClick={()=>setReload(n=>n+1)}>Làm mới</button></div>
    {loading&&<p role="status">Đang tải tổng quan…</p>}{error&&<p role="alert" className="notice warm">{error}</p>}
    <section className="card stack">
      <div className="between"><h2>Yêu cầu nấu cần phản hồi ({newRequests.length})</h2><Link to="/seller/requests" className="btn secondary sm">Xem yêu cầu & gửi báo giá</Link></div>
      {requestsError&&<p role="alert" className="notice warm">{requestsError}</p>}
      {newRequests.length?newRequests.slice(0,5).map(r=><div className="between soft" key={r.id}>
        <span>{r.requestCode} · {r.desiredDishes} · {r.mealDate}</span>
        <Link className="btn secondary sm" to="/seller/requests">Phản hồi</Link>
      </div>):<p className="muted">Không có yêu cầu mới được ghép. Kiểm tra trạng thái duyệt Chef, khu vực phục vụ và matching nếu khách vừa tạo yêu cầu.</p>}
    </section>
    {data&&<><div className="chef-metrics">
      {([['Đơn chờ nhận',data.pending],['Đang phục vụ',data.active],['Chờ nghiệm thu',data.awaitingAcceptance],['Đã hoàn thành',data.completed]] as const)
      .map(([label,value])=><article className="card chef-metric" key={label}><span className="small muted">{label}</span><strong className="chef-metric-value">{value}</strong></article>)}
    </div><section className="card stack"><div className="between"><h2>Đơn gần đây</h2><Link to="/seller/orders" className="text-link">Xem tất cả</Link></div>
      {data.recent.length?data.recent.map(o=><div key={o.id} className="between soft"><div><strong>{o.orderCode}</strong><p className="small muted">{o.productName} · {dateTime(o.scheduledAt)} · {o.status==='PENDING'&&o.escrowStatus!=='HELD'?'Chờ khách thanh toán':chefStatusLabels[o.status]}</p></div><Link className="btn secondary sm" to={`/seller/order-detail?id=${o.id}`}>Xem</Link></div>):<p className="muted">Chưa có đơn hàng thực tế.</p>}
    </section><section className="card stack"><div className="between"><h2>Lịch 7 ngày sắp tới</h2><Link className="text-link" to="/seller/schedule">Quản lý lịch</Link></div>
      <div className="chef-week">{slots.map(s=><Link key={s.date} to={`/seller/schedule?date=${s.date}`} className="chef-day"><span>{s.date.slice(5)}</span><strong>{s.bookings}</strong><small>{s.bookings?'Lịch đang giữ':s.available?'Đang mở lịch':'Chưa mở lịch'}</small></Link>)}</div>
      <p className="small muted">Số lịch giữ chỗ có thể bao gồm đơn chưa thanh toán VNPay; trạng thái tiền chính xác nằm trong mục Đơn đặt nấu.</p>
      <p className="small muted">Doanh thu thực nhận (escrow RELEASED) hiển thị tại <Link to="/seller/revenue">Doanh thu</Link>, không tính doanh thu giả theo tỷ lệ 85% trên đơn chưa nghiệm thu.</p>
    </section></>}
  </SellerWorkspace>;
}
