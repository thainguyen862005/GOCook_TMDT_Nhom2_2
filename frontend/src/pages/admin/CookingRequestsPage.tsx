import { useEffect, useState } from 'react';
import { AdminLayout } from '../../layouts/AdminLayout';
import { publicContentClient } from '../../clients/publicContentClient';
import { workflowClient } from '../../clients/workflowClient';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';

export function AdminCookingRequestsPage() {
  const [rows, setRows] = useState<Array<Record<string, unknown>>>([]);
  const [page, setPage] = useState(0);
  const [chefIds,setChefIds]=useState<Record<string,string>>({});
  const [message,setMessage]=useState('');
  const [actionError,setActionError]=useState('');
  const [reload,setReload]=useState(0);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true); setError('');
    publicContentClient.adminRequests(page).then(setRows)
      .catch(reason => setError(reason instanceof Error ? reason.message : 'Không tải được yêu cầu.'))
      .finally(() => setLoading(false));
  }, [page,reload]);
  async function assign(requestId:number){
    const id=Number(chefIds[String(requestId)]);
    if(!Number.isSafeInteger(id)||id<=0){setActionError('ID đầu bếp phải là số nguyên dương.');return;}
    try{setActionError('');await workflowClient.match(requestId,id);setMessage(`Đã ghép đầu bếp #${id} vào yêu cầu.`);setReload(n=>n+1);}
    catch(e){setActionError(e instanceof Error?e.message:'Không gán được đầu bếp.');}
  }
  return <AdminLayout title="Yêu cầu nấu ăn riêng" lead="Giám sát matching và hỗ trợ gán đầu bếp đã xác minh. Khách tự chấp nhận báo giá và thanh toán qua cổng hiện có.">
    {message&&<p className="notice" role="status">{message}</p>}
    {actionError&&<p className="notice warm" role="alert">{actionError}</p>}
    {loading ? <p role="status">Đang tải…</p> : error ? <p role="alert" className="notice warm">{error}</p>
      : rows.length === 0 ? <p>Chưa có yêu cầu ở trang này.</p> : rows.map(row => <article key={String(row.id)} className="card stack">
        <div className="between"><h3>{String(row.requestCode)}</h3><span className="badge">{String(row.status)}</span></div>
        <p>Khách hàng: {String(row.customerName)} · Ngày: {String(row.mealDate)} ({String(row.mealTime)})</p>
        <p>{String(row.desiredDishes)}</p><p className="small muted">{String(row.cuisine)} · {String(row.adults)} người lớn, {String(row.children)} trẻ em</p>
        <p className="small muted">Địa chỉ: {String(row.address)}</p>
        <p className="small muted">Dị ứng / kiêng: {String(row.avoid || 'Không có')}</p>
        <p className="small muted">Ngân sách: {String(row.budgetMin)} – {String(row.budgetMax)} đ</p>
        <div className="row"><Link className="btn secondary sm" to={`${ROUTES.REQUEST_DETAIL}?id=${row.id}`}>Xem matching & báo giá</Link>
          <label className="field">ID đầu bếp đã xác minh <input type="number" min={1} value={chefIds[String(row.id)]||''} onChange={e=>setChefIds(old=>({...old,[String(row.id)]:e.target.value}))} placeholder="Nhập ID" /></label>
          <button className="btn sm" type="button" disabled={row.status==='CLOSED'} onClick={()=>void assign(Number(row.id))}>Gán đầu bếp</button>
        </div>
        {typeof row.referenceImageUrl === 'string' && row.referenceImageUrl && <img className="admin-media-preview" src={row.referenceImageUrl} alt="Ảnh tham khảo của yêu cầu" />}
      </article>)}
    <div className="between"><button className="btn secondary" disabled={page === 0} onClick={() => setPage(p => p - 1)}>← Trước</button>
      <span>Trang {page + 1}</span><button className="btn secondary" disabled={rows.length < 30} onClick={() => setPage(p => p + 1)}>Sau →</button></div>
  </AdminLayout>;
}
