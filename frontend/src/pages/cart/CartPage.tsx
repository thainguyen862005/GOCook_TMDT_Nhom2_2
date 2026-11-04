import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { cartClient, type CartItem } from '../../clients/cartClient';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { money } from '../../util/format';

/** A menu = one Chef booking. Several menus can be kept, each checked out independently. */
export function CartPage() {
  useDocumentTitle('Giỏ thực đơn | GO Cook');
  const navigate=useNavigate();
  const [rows,setRows]=useState<CartItem[]>([]);
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState<number|null>(null);
  const [error,setError]=useState('');
  const [notice,setNotice]=useState('');
  const [revision,setRevision]=useState(0);
  useEffect(()=>{let alive=true;setLoading(true);setError('');
    cartClient.list().then(data=>{if(alive)setRows(data);}).catch(e=>{if(alive)setError(e instanceof Error?e.message:'Không tải được giỏ hàng.');})
      .finally(()=>{if(alive)setLoading(false);});return()=>{alive=false;};},[revision]);
  function edit(id:number,change:Partial<CartItem>) {setRows(current=>current.map(r=>r.id===id?{...r,...change}:r));setNotice('');}
  async function save(row:CartItem) {
    setBusy(row.id);setError('');setNotice('');
    try{await cartClient.update(row.id,row.guestCount,row.note??'');setNotice(`Đã cập nhật ${row.name}.`);setRevision(v=>v+1);}
    catch(e){setError(e instanceof Error?e.message:'Không thể cập nhật.');}finally{setBusy(null);}
  }
  async function checkoutItem(row:CartItem){
    if(busy!==null||!row.available)return;
    setBusy(row.id);setError('');
    try {await cartClient.update(row.id,row.guestCount,row.note||'');
      navigate(`${ROUTES.CHECKOUT}?${new URLSearchParams({productId:String(row.productId),guests:String(row.guestCount),cartItemId:String(row.id)})}`);
    } catch(e){setError(e instanceof Error?e.message:'Vui lòng lưu và kiểm tra lại thực đơn.');setRevision(n=>n+1);}
    finally{setBusy(null);}
  }
  async function remove(id:number){setBusy(id);setError('');setNotice('');
    try{await cartClient.remove(id);setRows(current=>current.filter(r=>r.id!==id));setNotice('Đã xóa thực đơn khỏi giỏ.');}
    catch(e){setError(e instanceof Error?e.message:'Không thể xóa thực đơn.');}finally{setBusy(null);}}
  return <div className="container section stack">
    <div className="page-heading"><nav className="breadcrumb"><Link to={ROUTES.HOME}>Trang chủ</Link><span>/</span><span>Giỏ thực đơn</span></nav>
      <h1>Giỏ thực đơn của bạn</h1><p className="lead">Lưu thực đơn yêu thích, chỉnh số khách, đặt lịch và thanh toán cho từng bữa ăn.</p></div>
    <p className="notice">Mỗi thực đơn là một booking với đầu bếp và lịch riêng. Bạn có thể lưu nhiều thực đơn nhưng thanh toán riêng từng booking để tránh trùng lịch và giao dịch.</p>
    {error&&<p role="alert" className="notice warm">{error} <button type="button" className="btn secondary sm" onClick={()=>setRevision(n=>n+1)}>Thử lại</button></p>}
    {notice&&<p role="status" className="notice">{notice}</p>}
    {loading?<p role="status">Đang tải giỏ hàng…</p>:rows.length===0?<section className="card stack"><h2>Chưa có thực đơn</h2><p>Khám phá các thực đơn đã công khai để bắt đầu.</p><Link className="btn" to={ROUTES.MENUS}>Xem thực đơn</Link></section>
      :<div className="stack">{rows.map(row=><section className="card stack" key={row.id}>
        <div className="order-item">{row.imageUrl&&<img src={row.imageUrl} alt={row.name} loading="lazy"/>}
          <div><h3><Link to={`${ROUTES.MENU_DETAIL}?id=${row.productId}`}>{row.name}</Link></h3><p className="muted small">{row.category} · {money(Number(row.unitPrice))}/người</p></div></div>
        {!row.available&&<p className="notice warm">Thực đơn đã thay đổi hoặc ngừng nhận đặt. Vui lòng chọn lại từ trang thực đơn.</p>}
        <div className="grid grid-2">
          <label className="field">Số người ăn<input type="number" min={row.minGuests} max={row.maxGuests} value={row.guestCount} onChange={e=>edit(row.id,{guestCount:Number(e.target.value)})}/></label>
          <label className="field">Ghi chú riêng cho đầu bếp<input type="text" maxLength={500} value={row.note??''} onChange={e=>edit(row.id,{note:e.target.value})} placeholder="Ít cay, tránh dị ứng…"/></label>
        </div>
        <div className="between"><strong>{money(Number(row.unitPrice)*row.guestCount)}</strong><div className="row">
          <button type="button" className="btn secondary sm" disabled={busy!==null||row.guestCount<row.minGuests||row.guestCount>row.maxGuests} onClick={()=>void save(row)}>Lưu thay đổi</button>
          <button type="button" className="btn ghost sm" disabled={busy!==null} onClick={()=>void remove(row.id)}>Xóa</button>
          {row.available&&<button type="button" className="btn sm" disabled={busy!==null||row.guestCount<row.minGuests||row.guestCount>row.maxGuests} onClick={()=>void checkoutItem(row)}>Chọn ngày & thanh toán</button>}
        </div></div>
      </section>)}</div>}
    <Link className="text-link" to={ROUTES.MENUS}>← Tiếp tục khám phá thực đơn</Link>
  </div>;
}
