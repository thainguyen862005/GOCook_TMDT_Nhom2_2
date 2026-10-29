import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { chefOrderClient } from '../../clients/chefOrderClient';
import { SellerWorkspace } from '../../components/common/SellerWorkspace';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { OrderEvidenceGallery } from '../../components/common/OrderEvidenceGallery';
import { ROUTES } from '../../constants/routes';
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
  const [evidenceType,setEvidenceType]=useState<'INGREDIENT_RECEIPT'|'FINISHED_MEAL'>('INGREDIENT_RECEIPT');
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
      await chefOrderClient.uploadReceipt(id,file,fileNote,evidenceType);setFile(null);setFileNote('');
      setNotice('Đã lưu ảnh vào hồ sơ đơn. Khách hàng có thể xem bằng chứng này.');setReload(v=>v+1);
    }catch(e){setError(e instanceof Error?e.message:'Không thể lưu hóa đơn.');}
    finally{setBusy(false);}
  }
  async function removeReceipt(receiptId:number){
    if(busy||!data)return;setBusy(true);setError('');
    try{await chefOrderClient.removeReceipt(id,receiptId);setNotice('Đã xóa ảnh chưa được chốt tiến độ.');setReload(v=>v+1);}
    catch(e){setError(e instanceof Error?e.message:'Không thể xóa hóa đơn.');}
    finally{setBusy(false);}
  }
  const o=data?.order;
  const receiptCount=data?.receipts.filter(r=>r.evidenceType==='INGREDIENT_RECEIPT').length??0;
  const mealCount=data?.receipts.filter(r=>r.evidenceType==='FINISHED_MEAL').length??0;
  const missingProof = o?.status==='PREPARING'&&receiptCount===0
    ? 'Cần có tối thiểu 1 hóa đơn mua nguyên liệu kèm tên cửa hàng/ngày mua trước khi bắt đầu nấu.'
    : o?.status==='COOKING'&&mealCount===0
      ? 'Cần có tối thiểu 1 ảnh món đã hoàn thành trước khi gửi khách nghiệm thu.' : '';
  return <SellerWorkspace title={o?`Đơn ${o.orderCode}`:'Chi tiết đơn đặt nấu'} description="Xử lý đơn đã được thanh toán và giữ lịch an toàn.">
    <div className="between"><Link to="/seller/orders" className="text-link">← Về danh sách đơn</Link><button className="btn secondary sm" onClick={()=>setReload(n=>n+1)} disabled={busy}>Tải lại</button></div>
    {loading&&<p role="status">Đang tải đơn…</p>}{error&&<p role="alert" className="notice warm">{error}</p>}{notice&&<p role="status" className="notice">{notice}</p>}
    {o&&data&&<><div className="chef-detail-grid"><section className="card stack">
      <div className="between"><h2>{o.productName}</h2><span className="badge">{o.status==='PENDING'&&o.escrowStatus!=='HELD'?'Chờ khách thanh toán':chefStatusLabels[o.status]}</span></div>
      <dl className="chef-definitions"><div><dt>Khách hàng</dt><dd>{o.customerName}</dd></div>
        <div><dt>Số khách</dt><dd>{o.guestCount}</dd></div><div><dt>Ngày và giờ</dt><dd>{dateTime(o.scheduledAt)}</dd></div>
        <div><dt>Thời lượng</dt><dd>{o.durationMinutes} phút</dd></div><div><dt>Địa chỉ</dt><dd>{o.serviceAddress||'Chưa được cung cấp'}</dd></div>
        <div><dt>Số điện thoại</dt><dd>{o.customerPhone||'Chưa được cung cấp'}</dd></div>
        <div><dt>Loại dịch vụ</dt><dd>{o.serviceType==='MENU'?'Thực đơn có sẵn':'Theo ngân sách'}</dd></div>
      </dl><div className="soft"><strong>Ghi chú khách hàng</strong><p>{o.customerNote||'Không có'}</p></div>
      <Link className="btn secondary" to={`${ROUTES.CHAT}?orderId=${o.id}`}>Nhắn tin với khách về đơn này</Link>
    </section><section className="card stack">
      <h2>Thanh toán và xử lý đơn</h2><h3>{money(o.totalAmount)}</h3>
      <p>Trạng thái tạm giữ: <strong>{o.escrowStatus==='HELD'?'Đang tạm giữ':o.escrowStatus==='RELEASED'?'Đã giải ngân':o.escrowStatus||'Khách chưa thanh toán qua VNPay'}</strong></p>
      <p className="small muted">Chỉ nhận đơn đã được Payment ghi nhận và escrow đang HELD. Chef không được tự giải ngân hoặc xác nhận thay khách.</p>
      {missingProof&&<p role="status" className="notice warm">{missingProof}</p>}
      {next[o.status]&&<button disabled={busy||o.escrowStatus!=='HELD'||Boolean(missingProof)} className="btn" onClick={()=>void change(next[o.status] as ChefOrderStatus)}>
        {o.status==='PENDING'?'Nhận đơn':o.status==='CONFIRMED'?'Bắt đầu chuẩn bị':o.status==='PREPARING'?'Bắt đầu nấu':'Báo đã phục vụ xong'}</button>}
      {o.status==='PENDING'&&<div className="stack-sm"><label className="field">Lý do từ chối (bắt buộc)
        <textarea rows={3} maxLength={1000} value={reason} onChange={e=>setReason(e.target.value)} placeholder="Giải thích lý do để khách hàng và bộ phận hỗ trợ nắm được"/></label>
        <button className="btn secondary" disabled={busy||o.escrowStatus!=='HELD'||reason.trim().length<10} onClick={()=>void change('REJECTED')}>Từ chối đơn và chuyển sang quy trình hoàn tiền</button>
      </div>}
      {o.status==='REJECTED'&&<p className="notice warm">Đã từ chối: {o.rejectedReason||'Không rõ lý do'}. Payment/Refund phụ trách hoàn tiền.</p>}
      {o.status==='AWAITING_ACCEPTANCE'&&<p>Đang chờ khách nghiệm thu. Khoản tạm giữ chưa được giải ngân.</p>}
    </section></div>
    <section className="card stack">
      <h2>Minh chứng thực hiện đơn</h2>
      <p className="small muted">Hóa đơn và ảnh món được lưu riêng tư; chỉ Chef và Customer của đơn này được xem. Ảnh hóa đơn giúp khách đối chiếu nơi mua, nhưng không thay thế việc xác thực nhà cung cấp. Hãy che thông tin thẻ, tài khoản hoặc số điện thoại không cần thiết trước khi gửi.</p>
      {['PREPARING','COOKING'].includes(o.status)&&<div className="quality-upload stack-sm">
        <label className="field">Loại ảnh
          <select value={evidenceType} onChange={e=>{setEvidenceType(e.target.value as 'INGREDIENT_RECEIPT'|'FINISHED_MEAL');setFile(null);setFileNote('');}}>
            <option value="INGREDIENT_RECEIPT">Hóa đơn mua nguyên liệu</option>
            {o.status==='COOKING'&&<option value="FINISHED_MEAL">Ảnh món ăn hoàn thành</option>}
          </select>
        </label>
        <label className="field">Ảnh JPG/PNG/WebP (tối đa 5 MB)
          <input key={`${evidenceType}-${reload}`} type="file" accept="image/jpeg,image/png,image/webp" onChange={e=>setFile(e.target.files?.[0]||null)}/>
        </label>
        <label className="field">{evidenceType==='INGREDIENT_RECEIPT'?'Tên cửa hàng, ngày mua, nguyên liệu':'Ghi chú món đã nấu'}
          <input value={fileNote} maxLength={500} onChange={e=>setFileNote(e.target.value)}
            placeholder={evidenceType==='INGREDIENT_RECEIPT'?'Ví dụ: Co.opmart, 29/10/2026, thịt bò và rau củ':'Ví dụ: Bò lúc lắc và rau củ, ảnh chụp trước khi phục vụ'}/>
        </label>
        <button className="btn secondary sm" disabled={!file||busy||(evidenceType==='INGREDIENT_RECEIPT'&&fileNote.trim().length<15)||
          (evidenceType==='INGREDIENT_RECEIPT'&&receiptCount>=10)||(evidenceType==='FINISHED_MEAL'&&mealCount>=5)}
          onClick={()=>void upload()}>Lưu ảnh & cho Customer xem</button>
      </div>}
      <OrderEvidenceGallery evidence={data.receipts} imagePath={evidenceId=>`/api/seller/orders/${id}/receipts/${evidenceId}/file`}
        canRemove={item=>(item.evidenceType==='INGREDIENT_RECEIPT'&&o.status==='PREPARING')||
          (item.evidenceType==='FINISHED_MEAL'&&o.status==='COOKING')}
        onRemove={evidenceId=>void removeReceipt(evidenceId)} busy={busy}/>
    </section>
    <section className="card stack"><h2>Lịch sử cập nhật</h2><ol className="chef-history">
      {data.history.map((h,i)=><li key={i}><strong>{chefStatusLabels[h.newStatus]||h.newStatus}</strong> · <span>{dateTime(h.changedAt)}</span>
        {h.note&&<p className="muted">{h.note}</p>}</li>)}
    </ol></section></>}
  </SellerWorkspace>;
}
