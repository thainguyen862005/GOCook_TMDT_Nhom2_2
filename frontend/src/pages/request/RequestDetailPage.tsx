import { provinceList } from '../../constants/provinces';
import { useEffect,useState } from 'react';
import { Link,useSearchParams } from 'react-router-dom';
import { workflowClient,type WorkflowRequest } from '../../clients/workflowClient';
import { paymentError } from '../../clients/paymentClient';
import { beginOrderPayment } from '../../hooks/checkout/orderPayment';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { money } from '../../util/format';
import { useAuth } from '../../hooks/auth/useAuth';

export function RequestDetailPage() {
  useDocumentTitle('Matching và báo giá | GO Cook');
  const { isAdmin } = useAuth();
  const [params]=useSearchParams();
  const id=Number(params.get('id'));
  const [data,setData]=useState<WorkflowRequest|null>(null);const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const [orderCode,setOrderCode]=useState('');
  const load=()=> { if(!Number.isSafeInteger(id)||id<1) {setError('Mã yêu cầu không hợp lệ.');setLoading(false);return;}
    workflowClient.detail(id).then(setData).catch(e=>setError(e.message)).finally(()=>setLoading(false)); };
  useEffect(() => { setData(null);setError('');setOrderCode('');setLoading(true);load(); },[id]);
  async function accept(offerId:number){
    if(busy || !window.confirm('Xác nhận chọn báo giá? Hệ thống sẽ tạo đơn chờ thanh toán, chưa trừ tiền.'))return;
    setBusy(true);setError('');
    try {
      const booked=await workflowClient.accept(id,offerId);
      setOrderCode(booked.orderCode);
      void workflowClient.detail(id).then(setData).catch(()=>{});
      // Acceptance only creates a booking. Payment begins immediately; no false 'confirmed' state.
      await beginOrderPayment(booked.orderCode);
    }
    catch(e){setError(e instanceof Error?e.message:'Không thể đặt lịch.');}finally{setBusy(false);}
  }
  async function pay(code:string){
    setBusy(true);setError('');
    try { await beginOrderPayment(code); }
    catch(e){setError(paymentError(e));setBusy(false);}
  }
  return <div className="container stack">
    <div className="page-heading"><nav className="breadcrumb"><Link to={ROUTES.MY_REQUESTS}>Yêu cầu của tôi</Link><span>/</span><span>Ghép đầu bếp & báo giá</span></nav><h1>{data?.requestCode || 'Chi tiết yêu cầu'}</h1><p className="lead">Ghép theo khu vực, phong cách và hồ sơ đã xác minh. Kết quả là gợi ý, không phải AI hay bảo đảm đầu bếp trống lịch.</p></div>
    <div><button className="btn secondary sm" onClick={()=>{setError('');setLoading(true);load();}}>Làm mới matching & báo giá</button></div>
    {loading&&<p role="status">Đang tải yêu cầu…</p>}
    {error&&<p className="notice warm" role="alert">{error}</p>}
    {data&&<><section className="card stack"><h2>Bữa ăn bạn yêu cầu</h2><p>{data.desiredDishes}</p><p>{data.adults+data.children} người · {data.mealDate} · {data.mealTime} · {data.address}</p><p>Ngân sách: {money(Number(data.budgetMin))} – {money(Number(data.budgetMax))}</p></section>
      <section className="stack"><h2>Đầu bếp được ghép ({data.matches?.length??0})</h2>{!data.matches?.length?<p className="notice">Chưa tìm thấy đầu bếp khả dụng. Admin có thể gán thêm đầu bếp.</p>:<div className="grid grid-2">{data.matches.map(m=><article className="card stack" key={m.chefId}><h3>{m.chefName}</h3><p>{provinceList(m.serviceArea)||'Chưa cập nhật khu vực'} · {Number(m.rating||0).toFixed(1)} / 5</p><p className="muted small">{m.matchReason}</p><div className="row"><Link className="btn secondary sm" to={`${ROUTES.CHEF_DETAIL}?id=${m.chefId}`}>Xem hồ sơ</Link>{!isAdmin && <Link className="btn sm" to={`${ROUTES.CHAT}?requestId=${id}&peerId=${m.chefId}`}>Nhắn cho đầu bếp</Link>}</div></article>)}</div>}</section>
      <section className="stack"><h2>Báo giá đã nhận ({data.offers?.length??0})</h2>{!data.offers?.length?<p className="notice">Đầu bếp chưa gửi báo giá. Bạn có thể nhắn tin để trao đổi chi tiết.</p>:data.offers.map(o=><article className="card stack" key={o.id}><div className="between"><h3>{o.chefName}</h3><strong className="price">{money(Number(o.price))}</strong></div><p>{o.dishes}</p>{o.note&&<p className="muted">{o.note}</p>}<p className="small">Trạng thái: {o.status}</p>
        {o.status==='SENT'&&Number(o.price)>Number(data.budgetMax)&&<p className="notice warm">Báo giá cao hơn ngân sách tối đa bạn nhập. Chỉ chấp nhận nếu bạn đồng ý mức giá này.</p>}
        {!isAdmin&&o.status==='SENT'&&!data.acceptedOrderId&&<button className="btn" disabled={busy} onClick={()=>void accept(o.id)}>{busy?'Đang chuyển sang VNPay…':'Chọn báo giá & thanh toán VNPay'}</button>}
        {!isAdmin && <Link className="text-link" to={`${ROUTES.CHAT}?requestId=${id}&peerId=${o.chefId}`}>Trao đổi với đầu bếp</Link>}</article>)}</section>
      {data.acceptedOrderId&&<section className="card stack"><h2>Đơn từ báo giá đã được tạo</h2><p>Đơn #{data.acceptedOrderId} · {data.acceptedOrderStatus==='CANCELLED'?'Đã hết hạn thanh toán':data.acceptedEscrowStatus==='HELD'?'Đã thanh toán, đang chờ Chef xác nhận':'Chưa xác nhận tiền VNPay'}. Chỉ khi có escrow HELD, Chef mới có thể nhận đơn.</p><Link className="btn secondary" to={`${ROUTES.ORDER_DETAIL}?id=${data.acceptedOrderId}`}>Xem chi tiết đơn</Link>{!isAdmin&&data.acceptedOrderStatus==='PENDING'&&data.acceptedEscrowStatus!=='HELD'&&(orderCode||data.acceptedOrderCode)&&<button disabled={busy} className="btn" onClick={()=>void pay(orderCode||data.acceptedOrderCode||'')}>{busy?'Đang chuyển…':'Tiếp tục thanh toán VNPay'}</button>}</section>}
    </>}
  </div>;
}
