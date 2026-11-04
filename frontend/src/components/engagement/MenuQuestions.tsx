import {useEffect,useState,type FormEvent} from 'react';
import {Link} from 'react-router-dom';
import {engagementClient,type MenuQuestion} from '../../clients/engagementClient';
import {useAuth} from '../../hooks/auth/useAuth';
import {ROUTES} from '../../constants/routes';
import {ReportButton} from './ReportButton';

export function MenuQuestions({productId}:{productId:number}) {
  const {profile,isAdmin,isSeller}=useAuth();
  const [rows,setRows]=useState<MenuQuestion[]>([]);
  const [page,setPage]=useState(0);const [totalPages,setTotalPages]=useState(0);
  const [question,setQuestion]=useState('');const [error,setError]=useState('');const [notice,setNotice]=useState('');
  const [busy,setBusy]=useState(false);const [loading,setLoading]=useState(true);
  const [mine,setMine]=useState<MenuQuestion[]>([]);
  const [revision,setRevision]=useState(0);
  useEffect(()=>{let active=true;setLoading(true);engagementClient.publicQuestions(productId,page)
    .then(result=>{if(active){setRows(result.content);setTotalPages(result.totalPages);}})
    .catch(e=>{if(active)setError(e instanceof Error?e.message:'Không tải được hỏi đáp.');})
    .finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[productId,page,revision]);
  useEffect(()=>{if(!profile||isAdmin||isSeller){setMine([]);return;}let alive=true;
    engagementClient.myQuestions().then(items=>{if(alive)setMine(items.filter(x=>x.productId===productId));}).catch(()=>{});
    return()=>{alive=false;};},[profile?.id,productId,isAdmin,isSeller,revision]);
  async function submit(e:FormEvent<HTMLFormElement>){e.preventDefault();if(busy)return;
    setBusy(true);setError('');setNotice('');
    try{await engagementClient.ask(productId,question.trim());setNotice('Đã gửi câu hỏi tới Chef. Câu hỏi sẽ hiện công khai sau khi Chef trả lời.');setQuestion('');setRevision(n=>n+1);}
    catch(err){setError(err instanceof Error?err.message:'Không gửi được câu hỏi.');}
    finally{setBusy(false);}}
  return <section className="card stack" aria-label="Hỏi đáp về thực đơn">
    <div className="between"><div><h2>Hỏi đáp về thực đơn</h2><p className="small muted">Đặt câu hỏi cho người đăng thực đơn. Đây không phải phần đánh giá sau mua.</p></div></div>
    {loading?<p role="status">Đang tải câu hỏi…</p>:rows.length===0?<p className="muted">Chưa có câu hỏi được trả lời.</p>
      :rows.map(row=><article key={row.id} className="soft stack-sm"><strong>Hỏi: {row.question}</strong>
        <p><strong>{row.chefName||'Đầu bếp'} trả lời:</strong> {row.answer}</p>
        {profile&&<ReportButton targetType="QUESTION" targetId={row.id} label="Báo cáo nội dung hỏi đáp"/>}
      </article>)}
    {totalPages>1&&<div className="row"><button className="btn secondary sm" disabled={page<=0} onClick={()=>setPage(n=>n-1)}>Trước</button>
      <span>Trang {page+1}/{totalPages}</span><button className="btn secondary sm" disabled={page+1>=totalPages} onClick={()=>setPage(n=>n+1)}>Sau</button></div>}
    {profile&&!isAdmin&&!isSeller?<form className="stack-sm" onSubmit={e=>void submit(e)}><label className="field">Đặt câu hỏi cho đầu bếp
      <textarea value={question} onChange={e=>setQuestion(e.target.value)} required minLength={10} maxLength={1500} rows={3} placeholder="Ví dụ: Thực đơn này có thể thay món chứa hải sản không?"/></label>
      <button className="btn secondary" disabled={busy||question.trim().length<10}>{busy?'Đang gửi…':'Gửi câu hỏi'}</button></form>
      :!profile?<p>Vui lòng <Link to={ROUTES.LOGIN}>đăng nhập</Link> để hỏi Chef.</p>:null}
    {mine.some(q=>q.status==='PENDING')&&<div className="soft stack-sm"><strong>Câu hỏi của tôi đang chờ Chef</strong>
      {mine.filter(q=>q.status==='PENDING').map(q=><p className="small" key={q.id}>{q.question}</p>)}</div>}
    {notice&&<p role="status" className="notice">{notice}</p>}{error&&<p role="alert" className="notice warm">{error}</p>}
  </section>;
}
