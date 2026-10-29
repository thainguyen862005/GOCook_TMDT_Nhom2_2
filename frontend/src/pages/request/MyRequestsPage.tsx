import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { workflowClient, type WorkflowRequest } from '../../clients/workflowClient';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { money } from '../../util/format';

export function MyRequestsPage() {
  useDocumentTitle('Yêu cầu của tôi | GO Cook');
  const [rows,setRows] = useState<WorkflowRequest[]>([]);
  const [error,setError] = useState('');
  const [loading,setLoading] = useState(true);
  const [reload,setReload] = useState(0);
  useEffect(() => { setLoading(true);workflowClient.mine().then(setRows).catch(e=>setError(e.message)).finally(()=>setLoading(false)); },[reload]);
  return <div className="container stack">
    <div className="page-heading"><h1>Yêu cầu nấu ăn của tôi</h1><p className="lead">Theo dõi đầu bếp được ghép và các báo giá của bạn.</p><div className="row"><Link className="btn" to={ROUTES.REQUEST}>+ Tạo yêu cầu mới</Link><button className="btn secondary" onClick={()=>setReload(n=>n+1)}>Làm mới</button></div></div>
    {loading?<p>Đang tải yêu cầu…</p>:error?<p role="alert" className="notice warm">{error}</p>:rows.length===0?<section className="card stack"><p>Bạn chưa có yêu cầu nào.</p><Link to={ROUTES.MATCHING}>Tìm đầu bếp phù hợp</Link></section>:rows.map(row=><article className="card stack" key={row.id}>
      <div className="between"><h3>{row.requestCode}</h3><span className="badge">{row.status==='CLOSED'?(row.acceptedOrderStatus==='CANCELLED'?'Đơn hết hạn':row.acceptedEscrowStatus==='HELD'?'Đã thanh toán, chờ Chef':'Chưa thanh toán'):row.status==='REVIEWING'?'Đang trao đổi':'Đang tìm đầu bếp'}</span></div>
      <p>{row.desiredDishes}</p><p className="muted small">Ngày {row.mealDate} · Ngân sách tối đa {money(Number(row.budgetMax))} · {row.offerCount ?? 0} báo giá chờ chọn</p>
      <Link className="btn secondary" to={`${ROUTES.REQUEST_DETAIL}?id=${row.id}`}>Xem matching, báo giá và chat</Link>
    </article>)}
  </div>;
}
