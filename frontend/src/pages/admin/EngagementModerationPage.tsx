import {useEffect,useState} from 'react';
import {AdminLayout} from '../../layouts/AdminLayout';
import {engagementClient,type MenuQuestion,type ContentReport} from '../../clients/engagementClient';
import {useDocumentTitle} from '../../hooks/common/useDocumentTitle';

export function EngagementModerationPage({kind}:{kind:'questions'|'reports'}){
 const questions=kind==='questions';useDocumentTitle(questions?'Kiểm duyệt hỏi đáp | GO Cook':'Báo cáo vi phạm | GO Cook');
 const [rows,setRows]=useState<Array<MenuQuestion|ContentReport>>([]);const [page,setPage]=useState(0),[pages,setPages]=useState(0);
 const [status,setStatus]=useState('');const [notes,setNotes]=useState<Record<number,string>>({});const [error,setError]=useState(''),[notice,setNotice]=useState('');
 const [busy,setBusy]=useState<number|null>(null);const [rev,setRev]=useState(0);
 useEffect(()=>{let alive=true;setError('');const promise=questions?engagementClient.adminQuestions(page,status):engagementClient.adminReports(page,status);
  promise.then(x=>{if(alive){setRows(x.content);setPages(x.totalPages);}}).catch(e=>{if(alive)setError(e instanceof Error?e.message:'Không tải được dữ liệu.');});
  return()=>{alive=false;};},[page,status,rev,questions]);
 async function act(row:MenuQuestion|ContentReport,action:string){if(busy!==null)return;setBusy(row.id);setError('');setNotice('');
  try{if(questions)await engagementClient.moderateQuestion(row.id,action==='HIDDEN');
   else await engagementClient.decideReport(row.id,action,(notes[row.id]||'').trim());
   setNotice('Đã lưu kết quả xử lý.');setRev(n=>n+1);}
  catch(e){setError(e instanceof Error?e.message:'Không lưu được kết quả.');}finally{setBusy(null);}}
 return <AdminLayout title={questions?'Kiểm duyệt hỏi đáp thực đơn':'Báo cáo gian hàng, khách hàng & nội dung'}
   lead={questions?'Kiểm soát câu hỏi và trả lời công khai, không xóa lịch sử.':'Admin tiếp nhận, xác minh và xử lý báo cáo có lý do; không tự khóa tài khoản.'}>
   <section className="card row"><label className="field">Trạng thái<select value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
     <option value="">Tất cả</option>{(questions?['PENDING','ANSWERED','HIDDEN']:['OPEN','IN_REVIEW','RESOLVED','DISMISSED']).map(x=><option key={x}>{x}</option>)}
   </select></label><button className="btn secondary sm" onClick={()=>setRev(n=>n+1)}>Làm mới</button></section>
   {error&&<p role="alert" className="notice warm">{error}</p>}{notice&&<p role="status" className="notice">{notice}</p>}
   {rows.length===0?<p className="card">Không có dữ liệu phù hợp.</p>:rows.map(row=><section key={row.id} className="card stack-sm">
      <div className="between"><strong>#{row.id} · {('targetType' in row)?row.targetType:row.menuName}</strong><span className="badge gray">{row.status}</span></div>
      {'question' in row?<><p><strong>Câu hỏi:</strong> {row.question}</p><p><strong>Trả lời:</strong> {row.answer||'Chưa có'}</p>
         <div className="row"><button disabled={busy!==null||row.status==='HIDDEN'} className="btn secondary sm" onClick={()=>void act(row,'HIDDEN')}>Ẩn</button>
           <button disabled={busy!==null||row.status!=='HIDDEN'} className="btn secondary sm" onClick={()=>void act(row,'VISIBLE')}>Khôi phục</button></div></>
        :<><p><strong>Đối tượng #{row.targetId}:</strong> {row.reason}</p>{row.orderId&&<p>Đơn liên quan: #{row.orderId}</p>}
          {['OPEN','IN_REVIEW'].includes(row.status)&&<><label className="field">Kết luận/lý do xử lý<textarea rows={3} minLength={10} maxLength={2000} value={notes[row.id]||''} onChange={e=>setNotes(x=>({...x,[row.id]:e.target.value}))}/></label>
            <div className="row">{['IN_REVIEW','RESOLVED','DISMISSED'].map(s=><button key={s} className="btn secondary sm" disabled={busy!==null||(notes[row.id]||'').trim().length<10} onClick={()=>void act(row,s)}>{s}</button>)}</div></>}
          {row.resolution&&<p><strong>Kết luận:</strong> {row.resolution}</p>}</>}
    </section>)}
    {pages>1&&<div className="row"><button className="btn secondary sm" disabled={!page} onClick={()=>setPage(n=>n-1)}>Trước</button><span>Trang {page+1}/{pages}</span>
      <button className="btn secondary sm" disabled={page+1>=pages} onClick={()=>setPage(n=>n+1)}>Sau</button></div>}
 </AdminLayout>;
}
