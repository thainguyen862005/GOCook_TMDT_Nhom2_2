import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { chefOrderClient } from '../../clients/chefOrderClient';
import { SellerWorkspace } from '../../components/common/SellerWorkspace';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { ChefOrderPage, ChefOrderStatus } from '../../types/chefOrder';
import { chefStatusLabels } from '../../types/chefOrder';
import { dateTime, money } from '../../util/format';

export function SellerOrdersPage(){
  useDocumentTitle('Đơn đặt nấu | GO Cook');
  const [status,setStatus]=useState('');
  const [from,setFrom]=useState('');
  const [to,setTo]=useState('');
  const [page,setPage]=useState(0);
  const [data,setData]=useState<ChefOrderPage|null>(null);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [reload,setReload]=useState(0);
  const invalid=Boolean(from&&to&&from>to);
  useEffect(()=>{
    if(invalid){setData(null);setLoading(false);return;}
    const ctrl=new AbortController();setLoading(true);setError('');
    chefOrderClient.list({status,from,to,page,size:10},ctrl.signal).then(setData)
      .catch(e=>{if(!ctrl.signal.aborted){setError(e instanceof Error?e.message:'Không tải được đơn.');setData(null);}})
      .finally(()=>{if(!ctrl.signal.aborted)setLoading(false);});
    return ()=>ctrl.abort();
  },[status,from,to,page,reload,invalid]);
  return <SellerWorkspace title="Đơn đặt nấu" description="Đơn thật từ cơ sở dữ liệu, chỉ hiển thị đơn được giao cho bạn.">
    <section className="card stack">
      <div className="chef-filters">
        <label className="field">Trạng thái<select value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
          <option value="">Tất cả</option>{Object.entries(chefStatusLabels).map(([value,label])=><option value={value} key={value}>{label}</option>)}
        </select></label>
        <label className="field">Từ ngày<input type="date" value={from} onChange={e=>{setFrom(e.target.value);setPage(0);}}/></label>
        <label className="field">Đến ngày<input type="date" value={to} onChange={e=>{setTo(e.target.value);setPage(0);}}/></label>
      </div>
      {invalid&&<p role="alert" className="notice warm">Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.</p>}
      <div className="row"><button className="btn secondary sm" onClick={()=>{setStatus('');setFrom('');setTo('');setPage(0);}}>Xóa bộ lọc</button>
      <button className="btn secondary sm" onClick={()=>setReload(n=>n+1)}>Tải lại</button></div>
    </section>
    <section className="card stack">
      {loading?<p role="status">Đang tải đơn đặt nấu…</p>:error?<p role="alert" className="notice warm">{error}</p>:
        <><h2>{data?.totalElements??0} đơn phù hợp</h2>
          <p className="muted small">Đơn PENDING chưa có escrow vẫn hiển thị để theo dõi, nhưng nút nhận đơn chỉ mở sau khi VNPay được xác thực. Các báo giá trước khi đặt lịch nằm ở mục Yêu cầu được ghép.</p><div className="table-wrap"><table><thead><tr>
          <th>Mã đơn / Khách</th><th>Thực đơn</th><th>Thời gian</th><th>Giá trị</th><th>Thanh toán</th><th>Trạng thái</th><th></th>
        </tr></thead><tbody>{data?.content.map(o=><tr key={o.id}><td><strong>{o.orderCode}</strong><div className="small muted">{o.customerName}</div></td>
          <td>{o.productName}<div className="small muted">{o.guestCount} khách · {o.serviceType==='MENU'?'Thực đơn':'Theo ngân sách'}</div></td>
          <td>{dateTime(o.scheduledAt)}</td><td>{money(o.totalAmount)}</td>
          <td>{o.escrowStatus==='HELD'?'Đã tạm giữ':o.escrowStatus==='RELEASED'?'Đã giải ngân':'Khách chưa thanh toán'}</td>
          <td><span className="badge">{o.status==='PENDING'&&o.escrowStatus!=='HELD'?'Chờ khách thanh toán':chefStatusLabels[o.status]||o.status}</span></td>
          <td><Link className="btn secondary sm" to={`/seller/order-detail?id=${o.id}`}>Chi tiết</Link></td></tr>)}</tbody></table></div>
          {!data?.content.length&&<p className="muted">Chưa có đơn phù hợp. Đơn vừa thanh toán cần được hệ thống Payment ghi nhận trước khi Chef nhận.</p>}
          <div className="between"><span>Trang {page+1} / {Math.max(1,data?.totalPages??1)}</span><div className="row">
            <button className="btn secondary sm" disabled={page<=0} onClick={()=>setPage(n=>Math.max(0,n-1))}>Trước</button>
            <button className="btn secondary sm" disabled={!data||page+1>=data.totalPages} onClick={()=>setPage(n=>n+1)}>Sau</button>
          </div></div></>}
    </section>
  </SellerWorkspace>;
}
