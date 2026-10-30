import { useEffect, useState } from 'react';

type Ward = {code:string;name:string;province:string};
type Props = {province:string;ward:string;onChange:(code:string)=>void};
export function WardSelect({province,ward,onChange}:Props) {
  const [wards,setWards]=useState<Ward[]>([]);
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState('');
  useEffect(()=>{
    setWards([]);setError('');
    if (!province) return;
    const ac=new AbortController();setLoading(true);
    fetch(`/api/public/wards?province=${encodeURIComponent(province)}`,{signal:ac.signal,headers:{Accept:'application/json'}})
      .then(async r=>{if(!r.ok)throw new Error('Không tải được danh sách phường/xã. Vui lòng kiểm tra bộ dữ liệu hành chính.');return r.json();})
      .then((data:unknown)=>{if(!Array.isArray(data))throw new Error('Dữ liệu phường/xã không hợp lệ.');
        setWards(data.filter((v):v is Ward=>v&&typeof v.code==='string'&&typeof v.name==='string'&&v.province===province));})
      .catch(e=>{if(!ac.signal.aborted)setError(e instanceof Error?e.message:'Không thể tải phường/xã.');})
      .finally(()=>{if(!ac.signal.aborted)setLoading(false);});
    return ()=>ac.abort();
  },[province]);
  return <label className="field">Phường/xã, đặc khu
    <select required disabled={!province||loading||!!error} value={ward} onChange={e=>onChange(e.target.value)}>
      <option value="">{loading?'Đang tải…':!province?'Chọn tỉnh/thành trước':'Chọn phường/xã'}</option>
      {wards.map(w=><option key={w.code} value={w.code}>{w.name}</option>)}
    </select>
    {error&&<span role="alert" className="small notice warm">{error}</span>}
    <span className="small muted">Chỉ hiển thị phường/xã thuộc tỉnh/thành đã chọn; danh mục theo mã hành chính 2025–2026.</span>
  </label>;
}
