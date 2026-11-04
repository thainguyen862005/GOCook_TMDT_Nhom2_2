import {useEffect,useState} from 'react';
import {Link} from 'react-router-dom';
import {engagementClient,type ContentReport} from '../../clients/engagementClient';
import {ROUTES} from '../../constants/routes';
import {useDocumentTitle} from '../../hooks/common/useDocumentTitle';
export function MyReportsPage(){
 useDocumentTitle('Báo cáo đã gửi | GO Cook');
 const [rows,setRows]=useState<ContentReport[]>([]);const [loading,setLoading]=useState(true),[error,setError]=useState('');
 const [reload,setReload]=useState(0);
 useEffect(()=>{let active=true;setLoading(true);engagementClient.myReports()
  .then(items=>{if(active)setRows(items);}).catch(e=>{if(active)setError(e instanceof Error?e.message:'Không tải được báo cáo.');})
  .finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[reload]);
 return <div className="container section stack"><div className="page-heading"><nav className="breadcrumb"><Link to={ROUTES.HOME}>Trang chủ</Link><span>/</span><span>Báo cáo đã gửi</span></nav>
  <h1>Báo cáo của tôi</h1><p className="lead">Theo dõi tình trạng xử lý và kết luận từ Admin.</p></div>
  <div className="row"><Link className="text-link" to={ROUTES.HELP}>Trung tâm trợ giúp</Link><button className="btn secondary sm" onClick={()=>setReload(v=>v+1)}>Tải lại</button></div>
  {loading?<p role="status">Đang tải…</p>:rows.length===0?<p className="card">Chưa có báo cáo nào được gửi từ tài khoản này.</p>:rows.map(r=><article className="card stack-sm" key={r.id}>
   <div className="between"><strong>Báo cáo #{r.id} · {r.targetType} #{r.targetId}</strong><span className="badge gray">{r.status}</span></div>
   <p><strong>Lý do:</strong> {r.reason}</p>{r.resolution&&<p><strong>Kết luận Admin:</strong> {r.resolution}</p>}
   <p className="small muted">{new Date(r.createdAt).toLocaleString('vi-VN')}</p>
  </article>)}
  {error&&<p role="alert" className="notice warm">{error}</p>}
 </div>;
}
