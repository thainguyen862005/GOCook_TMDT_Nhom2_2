import { useEffect, useRef, useState } from 'react';
import type { Coordinates } from '../../constants/serviceGeo';

type Candidate = Coordinates & {display:string};
type Props = {province:string;ward:string;address:string;chosen:Coordinates|null;onChoose:(point:Coordinates)=>void};

/** User-initiated address lookup only. Server validates province AND ward, then issues proof. */
export function LocationSearch({province,ward,address,chosen,onChoose}:Props) {
  const [options,setOptions]=useState<Candidate[]>([]);
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const queryKey=useRef('');queryKey.current=`${province}|${ward}|${address.trim()}`;
  useEffect(()=>{setOptions([]);setError('');},[province,ward,address]);
  async function lookup() {
    if (!province || !ward || address.trim().length<8 || busy) return;
    setBusy(true);setError('');setOptions([]);
    const expectedKey=queryKey.current;
    const controller=new AbortController();
    const timeout=window.setTimeout(()=>controller.abort(),14000);
    try {
      const uri=`/api/public/geocode?province=${encodeURIComponent(province)}&ward=${encodeURIComponent(ward)}&address=${encodeURIComponent(address.trim())}`;
      const response=await fetch(uri,{signal:controller.signal,headers:{Accept:'application/json'}});
      if (!response.ok) throw new Error(response.status===400?'Tỉnh hoặc phường/xã chưa hợp lệ. Vui lòng chọn lại.':'Không thể xác minh địa chỉ. Hãy thử lại sau.');
      const payload:unknown=await response.json();
      if (!Array.isArray(payload)) throw new Error('Kết quả tìm kiếm không hợp lệ.');
      const parsed=payload.flatMap((item):Candidate[]=>{
        if (!item||typeof item!=='object') return [];
        const r=item as Record<string,unknown>;const lat=Number(r.lat),lon=Number(r.lon);
        if (!Number.isFinite(lat)||!Number.isFinite(lon)||lat<7||lat>24||lon<101||lon>115
          ||typeof r.display_name!=='string'||typeof r.proof!=='string'||r.wardCode!==ward) return [];
        return [{lat,lon,label:r.display_name,display:r.display_name,wardCode:ward,proof:r.proof}];
      });
      if (expectedKey!==queryKey.current) return;
      setOptions(parsed);
      if (!parsed.length) setError('Chưa có kết quả xác thực đúng cả tỉnh/thành và phường/xã. Bản đồ có thể chưa cập nhật đơn vị hành chính mới; thử địa danh hoặc tên đường khác.');
    } catch(e) {
      if (expectedKey===queryKey.current) setError(e instanceof Error && e.name!=='AbortError'?e.message:'Tìm địa chỉ quá thời gian. Thử lại.');
    } finally {window.clearTimeout(timeout);setBusy(false);}
  }
  return <div className="stack-sm">
    <button className="btn secondary sm" type="button" onClick={()=>void lookup()} disabled={busy||!province||!ward||address.trim().length<8}>
      {busy?'Đang xác minh…':'Tìm và xác minh địa chỉ'}
    </button>
    {error&&<p role="alert" className="notice warm small">{error}</p>}
    {options.length>0&&<div className="card stack-sm"><strong>Chỉ các địa chỉ khớp đúng tỉnh/phường đã chọn:</strong>
      {options.map((candidate,i)=><button type="button" className="btn secondary sm" key={`${candidate.lat}-${candidate.lon}-${i}`}
        onClick={()=>{onChoose(candidate);setOptions([]);setError('');}}>{candidate.display}</button>)}
    </div>}
    {chosen&&<p role="status" className="notice small">Địa chỉ đã được xác minh: {chosen.label}. <a target="_blank" rel="noopener noreferrer"
      href={`https://www.openstreetmap.org/?mlat=${chosen.lat}&mlon=${chosen.lon}#map=15/${chosen.lat}/${chosen.lon}`}>Kiểm tra vị trí trên bản đồ</a></p>}
    <p className="small muted">Địa chỉ phải thuộc đúng tỉnh và phường/xã được chọn theo dữ liệu địa lý của OpenStreetMap. Khi dữ liệu bản đồ chưa cập nhật, hệ thống sẽ không chấp nhận vị trí chưa xác minh. Dữ liệu đơn vị hành chính: Địa Chỉ Việt (CC BY 4.0).</p>
  </div>;
}
