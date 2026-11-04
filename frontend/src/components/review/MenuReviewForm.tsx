import {useEffect,useState,type FormEvent} from 'react';
import {httpRequest} from '../../clients/httpClient';

interface Review {id:number;productId:number;rating:number;body:string;createdAt:string;customerName?:string;}
export function MenuReviews({menuId}:{menuId:number}){
 const [reviews,setReviews]=useState<Review[]>([]);const [error,setError]=useState('');
 useEffect(()=>{let active=true;httpRequest<Review[]>(`/api/public/menus/${menuId}/reviews`)
  .then(r=>{if(active)setReviews(r);}).catch(e=>{if(active)setError(e instanceof Error?e.message:'Không tải được đánh giá.');});
  return()=>{active=false;};},[menuId]);
 return <section className="card stack"><h2>Đánh giá thực đơn từ khách đã mua</h2>
  {reviews.length===0?<p className="muted">Chưa có đánh giá thực đơn đã được xác minh.</p>:<>
    <p><strong>{(reviews.reduce((sum,r)=>sum+r.rating,0)/reviews.length).toFixed(1)}/5</strong> · {reviews.length} đánh giá gần nhất</p>
    {reviews.map(r=><article key={r.id} className="soft stack-sm"><strong>{'★'.repeat(r.rating)}{'☆'.repeat(5-r.rating)}</strong><p>{r.body}</p><span className="small muted">{r.customerName||'Khách đã mua'}</span></article>)}
  </>}
  {error&&<p role="alert" className="notice warm">{error}</p>}
 </section>;
}
export function MenuReviewForm({orderId}:{orderId:number}){
 const [eligible,setEligible]=useState(false);const [prior,setPrior]=useState<Review|null>(null);
 const [rating,setRating]=useState(5),[body,setBody]=useState('');const [busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{let active=true;Promise.all([
  httpRequest<{eligible:boolean}>(`/api/customer/orders/${orderId}/menu-review/eligibility`),
  httpRequest<Review[]>(`/api/customer/orders/${orderId}/menu-review`)
 ]).then(([valid,results])=>{if(active){setEligible(valid.eligible);setPrior(results[0]||null);}})
 .catch(e=>{if(active)setError(e instanceof Error?e.message:'Không kiểm tra được quyền đánh giá.');});return()=>{active=false;};},[orderId]);
 async function submit(e:FormEvent){e.preventDefault();if(busy)return;setBusy(true);setError('');
  try{await httpRequest(`/api/customer/orders/${orderId}/menu-review`,{method:'POST',body:JSON.stringify({rating,body:body.trim()})});
   setPrior((await httpRequest<Review[]>(`/api/customer/orders/${orderId}/menu-review`))[0]||null);}
  catch(err){setError(err instanceof Error?err.message:'Không gửi được đánh giá.');}finally{setBusy(false);}}
 if(!eligible&&!prior&&!error)return null;
 return <section className="card stack"><h3>Đánh giá thực đơn đã mua</h3><p className="small muted">Độc lập với đánh giá đầu bếp. Chỉ áp dụng cho thực đơn có trong đơn đã nghiệm thu.</p>
   {prior?<p><strong>{prior.rating}/5 sao</strong> · {prior.body}</p>:eligible&&<form className="stack-sm" onSubmit={e=>void submit(e)}>
     <label className="field">Số sao<select value={rating} onChange={e=>setRating(Number(e.target.value))}>{[5,4,3,2,1].map(n=><option key={n} value={n}>{n}/5</option>)}</select></label>
     <label className="field">Đánh giá món ăn<textarea minLength={15} maxLength={1000} required value={body} onChange={e=>setBody(e.target.value)} placeholder="Chất lượng món, khẩu vị, khẩu phần…" rows={3}/></label>
     <button className="btn secondary" disabled={busy||body.trim().length<15}>{busy?'Đang gửi…':'Đánh giá thực đơn'}</button></form>}
   {error&&<p role="alert" className="notice warm">{error}</p>}
 </section>;
}
