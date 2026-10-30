import { useEffect, useState, type FormEvent } from 'react';
import { reviewClient, type OrderReview } from '../../clients/reviewClient';

export function OrderReviewForm({orderId}:{orderId:number}) {
  const [prior,setPrior]=useState<OrderReview|null>(null);
  const [rating,setRating]=useState(5);
  const [body,setBody]=useState('');
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  useEffect(()=>{let active=true;setLoading(true);reviewClient.own(orderId)
    .then(r=>{if(active)setPrior(r[0]||null);}).catch(e=>{if(active)setError(e instanceof Error?e.message:'Không tải được đánh giá.');})
    .finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[orderId]);
  async function submit(e:FormEvent){e.preventDefault();if(busy)return;setBusy(true);setError('');
    try{await reviewClient.publish(orderId,{rating,body:body.trim()});setPrior((await reviewClient.own(orderId))[0]||null);}
    catch(e){setError(e instanceof Error?e.message:'Không gửi được đánh giá.');}finally{setBusy(false);}}
  return <section className="card stack"><h3>Đánh giá đầu bếp</h3>
    {loading?<p>Đang tải đánh giá…</p>:prior?<div className="stack-sm"><strong>{'★'.repeat(prior.rating)}{'☆'.repeat(5-prior.rating)}</strong><p>{prior.body}</p><p className="small muted">Đánh giá từ đơn hàng đã hoàn thành.</p></div>
      :<form className="stack" onSubmit={e=>void submit(e)}>
        <label className="field">Mức hài lòng<select value={rating} onChange={e=>setRating(Number(e.target.value))}>
          {[5,4,3,2,1].map(n=><option key={n} value={n}>{n} / 5 sao</option>)}
        </select></label>
        <label className="field">Nhận xét<textarea value={body} onChange={e=>setBody(e.target.value)} minLength={15} maxLength={1000} required placeholder="Đánh giá chất lượng món ăn, đúng giờ, vệ sinh…" rows={4}/></label>
        <button className="btn" disabled={busy||body.trim().length<15}>{busy?'Đang gửi…':'Đăng đánh giá'}</button>
      </form>}
    {error&&<p role="alert" className="notice warm">{error}</p>}
  </section>;
}
