import {useEffect,useState} from 'react';
import {Link} from 'react-router-dom';
import {engagementClient,type MenuQuestion} from '../../clients/engagementClient';
import {SellerWorkspace} from '../../components/common/SellerWorkspace';
import {ROUTES} from '../../constants/routes';
import {useDocumentTitle} from '../../hooks/common/useDocumentTitle';

export function SellerMenuQuestionsPage(){
 useDocumentTitle('Hỏi đáp thực đơn | GO Cook');
 const [rows,setRows]=useState<MenuQuestion[]>([]);const [page,setPage]=useState(0);const [pages,setPages]=useState(0);
 const [status,setStatus]=useState('PENDING');const [answers,setAnswers]=useState<Record<number,string>>({});
 const [error,setError]=useState('');const [notice,setNotice]=useState('');const [busy,setBusy]=useState<number|null>(null);
 const [revision,setRevision]=useState(0);
 useEffect(()=>{let alive=true;setError('');engagementClient.chefQuestions(page,status)
  .then(d=>{if(alive){setRows(d.content);setPages(d.totalPages);}})
  .catch(e=>{if(alive)setError(e instanceof Error?e.message:'Không tải được câu hỏi.');});return()=>{alive=false;};},[page,status,revision]);
 async function reply(id:number){if(busy!==null)return;setBusy(id);setError('');setNotice('');
  try{await engagementClient.answer(id,(answers[id]||'').trim());setNotice('Đã trả lời. Khách có thể xem trên trang thực đơn.');setRevision(v=>v+1);}
  catch(e){setError(e instanceof Error?e.message:'Không gửi được trả lời.');}finally{setBusy(null);}}
 return <SellerWorkspace title="Hỏi đáp thực đơn" description="Đọc câu hỏi thực tế từ khách và trả lời công khai sau khi kiểm tra nội dung.">
  <section className="card row"><label className="field">Trạng thái<select value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
    <option value="">Tất cả</option><option value="PENDING">Chưa trả lời</option><option value="ANSWERED">Đã trả lời</option><option value="HIDDEN">Đã ẩn</option></select></label>
    <button className="btn secondary sm" onClick={()=>setRevision(n=>n+1)}>Tải lại</button></section>
  {error&&<p role="alert" className="notice warm">{error}</p>}{notice&&<p role="status" className="notice">{notice}</p>}
  {rows.length===0?<p className="card">Không có câu hỏi phù hợp.</p>:rows.map(q=><section key={q.id} className="card stack-sm">
   <div className="between"><h3>{q.menuName}</h3><span className="badge gray">{q.status}</span></div>
   <p><strong>Khách hỏi:</strong> {q.question}</p><Link className="text-link small" to={`${ROUTES.MENU_DETAIL}?id=${q.productId}`}>Xem thực đơn</Link>
   {q.status==='PENDING'?<><label className="field">Câu trả lời<textarea rows={4} maxLength={2000} value={answers[q.id]||''} onChange={e=>setAnswers(x=>({...x,[q.id]:e.target.value}))}/></label>
     <button className="btn" disabled={busy!==null||(answers[q.id]||'').trim().length<5} onClick={()=>void reply(q.id)}>Trả lời</button></>
    :<p><strong>Đã trả lời:</strong> {q.answer||'Chưa có'}</p>}
  </section>)}
  {pages>1&&<div className="row"><button className="btn secondary sm" disabled={!page} onClick={()=>setPage(n=>n-1)}>Trước</button>
   <span>Trang {page+1}/{pages}</span><button className="btn secondary sm" disabled={page+1>=pages} onClick={()=>setPage(n=>n+1)}>Sau</button></div>}
 </SellerWorkspace>;
}
