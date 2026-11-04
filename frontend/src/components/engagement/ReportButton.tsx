import {useState,type FormEvent} from 'react';
import {Link} from 'react-router-dom';
import {engagementClient} from '../../clients/engagementClient';
import {useAuth} from '../../hooks/auth/useAuth';
import {ROUTES} from '../../constants/routes';

/** CUSTOMER uses an order ID (not a free-form user ID); backend derives the customer. */
export function ReportButton({targetType,targetId,label='Báo cáo vi phạm'}:{targetType:'CHEF'|'CUSTOMER'|'MENU'|'QUESTION';targetId:number;label?:string}){
 const {profile}=useAuth();const [expanded,setExpanded]=useState(false);const [reason,setReason]=useState('');
 const [busy,setBusy]=useState(false);const [message,setMessage]=useState('');const [sent,setSent]=useState(false);
 if(!profile)return <Link className="text-link small" to={ROUTES.LOGIN}>Đăng nhập để báo cáo</Link>;
 async function submit(event:FormEvent){event.preventDefault();if(busy||sent)return;setBusy(true);setMessage('');
  try{await engagementClient.report(targetType,targetId,reason.trim());setMessage('Báo cáo đã được chuyển tới Admin để xem xét.');setSent(true);setReason('');}
  catch(e){setMessage(e instanceof Error?e.message:'Không gửi được báo cáo.');}finally{setBusy(false);}}
 return <div className="stack-sm"><button type="button" className="text-link small" onClick={()=>setExpanded(v=>!v)} disabled={sent}>{sent?'Đã gửi báo cáo':label}</button>
   {expanded&&<form className="stack-sm" onSubmit={e=>void submit(e)}><label className="field">Lý do báo cáo
     <textarea minLength={15} maxLength={1500} rows={3} required value={reason} onChange={e=>setReason(e.target.value)} placeholder="Mô tả cụ thể hành vi hoặc nội dung không phù hợp"/></label>
     <div className="row"><button type="submit" className="btn secondary sm" disabled={busy||reason.trim().length<15||sent}>{busy?'Đang gửi…':'Gửi báo cáo'}</button>
       <button type="button" className="btn ghost sm" onClick={()=>setExpanded(false)}>Đóng</button></div></form>}
   {message&&<p role="status" className="small muted">{message}</p>}
 </div>;
}
