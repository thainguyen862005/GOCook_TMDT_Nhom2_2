import { useEffect,useState,type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { workflowClient,type WorkflowRequest } from '../../clients/workflowClient';
import { SellerWorkspace } from '../../components/common/SellerWorkspace';
import { ROUTES } from '../../constants/routes';
import { money } from '../../util/format';

export function SellerRequestsPage(){
  const [rows,setRows]=useState<(WorkflowRequest & {myPrice?:number;myOfferStatus?:string})[]>([]);
  const [error,setError]=useState('');const [working,setWorking]=useState<number|null>(null);
  const load=()=>workflowClient.chefRequests().then(setRows).catch(e=>setError(e.message));
  useEffect(()=>{ void load(); const timer=window.setInterval(()=>{if(document.visibilityState==='visible')void load();},15000); return ()=>window.clearInterval(timer); },[]);
  async function offer(e:FormEvent<HTMLFormElement>,id:number){e.preventDefault();const f=new FormData(e.currentTarget);setWorking(id);setError('');
    try{await workflowClient.offer(id,{price:Number(f.get('price')),dishes:String(f.get('dishes')).trim(),note:String(f.get('note')).trim()});await load();}
    catch(e){setError(e instanceof Error?e.message:'Không thể gửi báo giá.');}finally{setWorking(null);}}
  return <SellerWorkspace title="Yêu cầu được ghép" description="Nhận yêu cầu đã được ghép, trao đổi hai chiều với khách và gửi báo giá trước khi đặt lịch.">
    <div className="row"><button type="button" className="btn secondary sm" onClick={()=>void load()}>Làm mới danh sách</button><Link className="btn secondary sm" to={ROUTES.MESSAGES}>Hộp thư Chef</Link></div>
    {error&&<p className="notice warm" role="alert">{error}</p>}
    <p className="small muted">Đang chờ phản hồi: <strong>{rows.filter(row=>row.status!=='CLOSED'&&!row.myPrice).length}</strong> · Tổng yêu cầu được ghép: <strong>{rows.length}</strong>. Danh sách tự cập nhật mỗi 15 giây khi tab đang mở.</p>
    {rows.length===0?<section className="card">Chưa có yêu cầu được ghép. Hãy đảm bảo hồ sơ được xác minh và khu vực phục vụ phù hợp.</section>:rows.map(r=><section className="card stack" key={r.id}>
      <div className="between"><h3>{r.requestCode}</h3><span className="badge">{r.status==='CLOSED'?(r.myOfferStatus==='ACCEPTED'?'Báo giá được chọn':'Khách đã chốt Chef khác'):'Đang nhận báo giá'}</span></div>
      <p>{r.desiredDishes}</p><p>Khách: {r.customerName || `#${r.customerId}`} · Khu vực: {r.serviceArea || 'Chưa chỉ định'}</p><p>{r.cuisine} · {r.adults+r.children} người · {r.mealDate} ({r.mealTime})</p>
      <p className="muted">Địa chỉ: {r.address} · Ngân sách {money(Number(r.budgetMin))}–{money(Number(r.budgetMax))}</p>
      <p>Báo giá hiện tại: {r.myPrice?money(Number(r.myPrice)):'Chưa gửi'}</p>
      {r.myOfferStatus==='ACCEPTED'&&<div className="soft stack">
        <strong>Khách đã chọn báo giá của bạn</strong>
        <p>{r.myEscrowStatus==='HELD'?'Khách đã thanh toán và tiền đang được tạm giữ. Bạn có thể nhận đơn.':'Đang chờ khách thanh toán VNPay. Chưa nhận nấu hoặc mua nguyên liệu.'}</p>
        {r.myOrderId&&<Link className="btn secondary sm" to={`/seller/order-detail?id=${r.myOrderId}`}>Xem đơn {r.myOrderCode}</Link>}
      </div>}
      {r.status!=='CLOSED'&&<form className="stack" onSubmit={e=>void offer(e,r.id)}>
        <label className="field">Giá đề xuất (VND)<input name="price" type="number" min="1" max="1000000000" defaultValue={r.myPrice||r.budgetMax} required /></label>
        <label className="field">Món ăn phục vụ<textarea name="dishes" defaultValue={r.desiredDishes} maxLength={3000} required /></label>
        <label className="field">Ghi chú báo giá<textarea name="note" maxLength={2000} /></label>
        <button className="btn" disabled={working===r.id}>{working===r.id?'Đang gửi…':r.myPrice?'Cập nhật báo giá':'Gửi báo giá'}</button>
      </form>}
      <Link className="btn secondary" to={`${ROUTES.CHAT}?requestId=${r.id}&peerId=${r.customerId}`}>Nhắn tin với khách</Link>
    </section>)}
  </SellerWorkspace>;
}
