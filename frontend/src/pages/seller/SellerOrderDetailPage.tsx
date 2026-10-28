import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { chefOrderClient } from '../../clients/chefOrderClient';
import { SellerWorkspace } from '../../components/common/SellerWorkspace';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { ChefOrderDetail, ChefOrderStatus } from '../../types/chefOrder';
import { chefStatusLabels } from '../../types/chefOrder';
import { dateTime, money } from '../../util/format';

const next:Partial<Record<ChefOrderStatus,ChefOrderStatus>>={
  PENDING:'CONFIRMED',CONFIRMED:'PREPARING',PREPARING:'COOKING',COOKING:'AWAITING_ACCEPTANCE',
};
export function SellerOrderDetailPage(){
  useDocumentTitle('Chi tiết đơn Chef | GO Cook');
  const [params]=useSearchParams();
  const id=Number(params.get('id'));const valid=Number.isSafeInteger(id)&&id>0;
  const [data,setData]=useState<ChefOrderDetail|null>(null);
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');const [notice,setNotice]=useState('');
  const [reason,setReason]=useState('');
  const [file,setFile]=useState<File|null>(null);
  const [fileNote,setFileNote]=useState('');
  const [reload,setReload]=useState(0);
  useEffect(()=>{
    if(!valid){setLoading(false);setError('Mã đơn không hợp lệ.');return;}
    const ctrl=new AbortController();setLoading(true);setError('');
    chefOrderClient.get(id,ctrl.signal).then(setData).catch(e=>{if(!ctrl.signal.aborted){setData(null);setError(e instanceof Error?e.message:'Không thể tải đơn.');}})
      .finally(()=>{if(!ctrl.signal.aborted)setLoading(false);});
    return ()=>ctrl.abort();
  },[id,valid,reload]);
  async function change(target:ChefOrderStatus){
    if(!data||busy)return;
    if(target==='REJECTED'&&(reason.trim().length<10||reason.trim().length>1000)){
      setError('Lý do từ chối cần 10–1000 ký tự.');return;
    }
    setBusy(true);setError('');setNotice('');
    try{
      const result=await chefOrderClient.transition(data.order,target,reason.trim());
      setData(result);setReason('');setNotice(target==='AWAITING_ACCEPTANCE'?
        'Đã báo hoàn thành. Chỉ khách hàng mới có thể nghiệm thu và giải ngân.':`Đã cập nhật: ${chefStatusLabels[target]}.`);
    }catch(e){setError(e instanceof Error?e.message:'Không thể cập nhật. Hãy tải lại.');}
    finally{setBusy(false);}
  }
  async function upload(){
    if(!file||!data||busy)return;
    if(file.size===0||file.size>5*1024*1024||!['image/jpeg','image/png','image/webp'].includes(file.type)){
      setError('Chỉ nhận ảnh JPG, PNG, WebP tối đa 5 MB.');return;
    }
    setBusy(true);setError('');try{
      await chefOrderClient.uploadReceipt(id,file,fileNote);setFile(null);setFileNote('');
      setNotice('Đã tải hóa đơn lên vùng lưu trữ riêng tư.');setReload(v=>v+1);
    }catch(e){setError(e instanceof Error?e.message:'Không thể lưu hóa đơn.');}
    finally{setBusy(false);}
  }
  async function removeReceipt(receiptId:number){
    if(busy||!data)return;setBusy(true);setError('');
    try{await chefOrderClient.removeReceipt(id,receiptId);setNotice('Đã ẩn hóa đơn.');setReload(v=>v+1);}
    catch(e){setError(e instanceof Error?e.message:'Không thể xóa hóa đơn.');}
    finally{setBusy(false);}
  }
  const o=data?.order;
  return <SellerWorkspace title={o?`Đơn ${o.orderCode}`:'Chi tiết đơn đặt nấu'} description="Xử lý đơn đã được thanh toán và giữ lịch an toàn.">
    <div className="between"><Link to="/seller/orders" className="text-link">← Về danh sách đơn</Link><button className="btn secondary sm" onClick={()=>setReload(n=>n+1)} disabled={busy}>Tải lại</button></div>
    {loading&&<p role="status">Đang tải đơn…</p>}{error&&<p role="alert" className="notice warm">{error}</p>}{notice&&<p role="status" className="notice">{notice}</p>}
    {o&&data&&<><div className="chef-detail-grid"><section className="card stack">
      <div className="between"><h2>{o.productName}</h2><span className="badge">{chefStatusLabels[o.status]}</span></div>
      <dl className="chef-definitions"><div><dt>Khách hàng</dt><dd>{o.customerName}</dd></div>
        <div><dt>Số khách</dt><dd>{o.guestCount}</dd></div><div><dt>Ngày và giờ</dt><dd>{dateTime(o.scheduledAt)}</dd></div>
        <div><dt>Thời lượng</dt><dd>{o.durationMinutes} phút</dd></div><div><dt>Địa chỉ</dt><dd>{o.serviceAddress||'Chưa được cung cấp'}</dd></div>
        <div><dt>Số điện thoại</dt><dd>{o.customerPhone||'Chưa được cung cấp'}</dd></div>
        <div><dt>Loại dịch vụ</dt><dd>{o.serviceType==='MENU'?'Thực đơn có sẵn':'Theo ngân sách'}</dd></div>
      </dl><div className="soft"><strong>Ghi chú khách hàng</strong><p>{o.customerNote||'Không có'}</p></div>
      <p className="small muted">Trao đổi với khách: tích hợp Chat theo mã đơn {o.orderCode} khi module Chat của nhóm sẵn sàng.</p>
    </section><section className="card stack">
      <h2>Thanh toán và xử lý đơn</h2><h3>{money(o.totalAmount)}</h3>
      <p>Trạng thái tạm giữ: <strong>{o.escrowStatus==='HELD'?'Đang tạm giữ':o.escrowStatus==='RELEASED'?'Đã giải ngân':o.escrowStatus||'Chưa xác nhận'}</strong></p>
      <p className="small muted">Chỉ nhận đơn đã được Payment ghi nhận và escrow đang HELD. Chef không được tự giải ngân hoặc xác nhận thay khách.</p>
      {next[o.status]&&<button disabled={busy||o.escrowStatus!=='HELD'} className="btn" onClick={()=>void change(next[o.status] as ChefOrderStatus)}>
        {o.status==='PENDING'?'Nhận đơn':o.status==='CONFIRMED'?'Bắt đầu chuẩn bị':o.status==='PREPARING'?'Bắt đầu nấu':'Báo đã phục vụ xong'}</button>}
      {o.status==='PENDING'&&<div className="stack-sm"><label className="field">Lý do từ chối (bắt buộc)
        <textarea rows={3} maxLength={1000} value={reason} onChange={e=>setReason(e.target.value)} placeholder="Giải thích lý do để khách hàng và bộ phận hỗ trợ nắm được"/></label>
        <button className="btn secondary" disabled={busy||o.escrowStatus!=='HELD'||reason.trim().length<10} onClick={()=>void change('REJECTED')}>Từ chối đơn và chuyển sang quy trình hoàn tiền</button>
      </div>}
      {o.status==='REJECTED'&&<p className="notice warm">Đã từ chối: {o.rejectedReason||'Không rõ lý do'}. Payment/Refund phụ trách hoàn tiền.</p>}
      {o.status==='AWAITING_ACCEPTANCE'&&<p>Đang chờ khách nghiệm thu. Khoản tạm giữ chưa được giải ngân.</p>}
    </section></div>
    <section className="card stack"><h2>Hóa đơn mua nguyên liệu (lưu riêng tư)</h2>
      <p className="small muted">Chỉ Chef của đơn này có thể tải/xem hóa đơn. Hóa đơn không công khai trên Cloudinary hoặc giao diện khách hàng.</p>
      {['PREPARING','COOKING'].includes(o.status)&&<div className="stack-sm">
        <label className="field">Chọn ảnh JPG/PNG/WebP tối đa 5 MB<input type="file" accept="image/jpeg,image/png,image/webp" onChange={e=>setFile(e.target.files?.[0]||null)}/></label>
        <label className="field">Ghi chú hóa đơn<input value={fileNote} maxLength={500} onChange={e=>setFileNote(e.target.value)}/></label>
        <button className="btn secondary sm" disabled={!file||busy||data.receipts.length>=10} onClick={()=>void upload()}>Tải hóa đơn lên</button>
      </div>}
      {data.receipts.length?data.receipts.map(r=><div className="between soft" key={r.id}>
        <span><strong>{r.filename}</strong><br/><small>{r.note}</small></span><div className="row">
          <a className="btn secondary sm" target="_blank" rel="noreferrer" href={r.fileUrl}>Xem</a>
          {['PREPARING','COOKING'].includes(o.status)&&<button className="btn ghost sm" disabled={busy} onClick={()=>void removeReceipt(r.id)}>Ẩn</button>}
        </div></div>):<p className="muted">Chưa có hóa đơn.</p>}
    </section>
    <section className="card stack"><h2>Lịch sử cập nhật</h2><ol className="chef-history">
      {data.history.map((h,i)=><li key={i}><strong>{chefStatusLabels[h.newStatus]||h.newStatus}</strong> · <span>{dateTime(h.changedAt)}</span>
        {h.note&&<p className="muted">{h.note}</p>}</li>)}
    </ol></section></>}
  </SellerWorkspace>;
}
