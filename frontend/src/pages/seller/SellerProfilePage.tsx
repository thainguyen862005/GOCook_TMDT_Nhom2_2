import { useEffect,useState,type FormEvent } from 'react';
import { SellerWorkspace } from '../../components/common/SellerWorkspace';
import { workflowClient } from '../../clients/workflowClient';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import { PROVINCES } from '../../constants/provinces';
import { LocationSearch } from '../../components/location/LocationSearch';
import { WardSelect } from '../../components/location/WardSelect';
import { parseLocation, RADII, type Coordinates } from '../../constants/serviceGeo';

export function SellerProfilePage(){
  const [form,setForm]=useState({displayName:'',bio:'',serviceArea:'',phone:''});
  const [province,setProvince]=useState('');
  const [ward,setWard]=useState('');
  const [startAddress,setStartAddress]=useState('');
  const [coords,setCoords]=useState<Coordinates|null>(null);
  const [radius,setRadius]=useState(15);
  const [error,setError]=useState('');const [saved,setSaved]=useState(false);const [busy,setBusy]=useState(false);
  const [loading,setLoading]=useState(true);
  useEffect(()=>{let active=true; workflowClient.chefProfile()
    .then(p=>{if(active){const raw=p.serviceArea||'';
      const parsed=parseLocation(raw);
      setProvince(PROVINCES.some(item=>item.code===parsed.province)?parsed.province:'');
      setWard(raw.split('|')[4]||''); setCoords(null);setRadius(parsed.radius);
      if(raw)setError('Vui lòng xác nhận lại địa chỉ theo tỉnh và phường/xã để bảo đảm thông tin vị trí chính xác.');
      setForm({displayName:p.displayName||'',bio:p.bio||'',serviceArea:raw,phone:p.phone||''});}})
    .catch(e=>{if(active)setError(e instanceof Error?e.message:'Không tải được hồ sơ đầu bếp.');})
    .finally(()=>{if(active)setLoading(false);});
    return ()=>{active=false;};},[]);
  async function save(e:FormEvent){e.preventDefault();setBusy(true);setError('');setSaved(false);
    try{if(!province||!ward||!coords?.proof||coords.wardCode!==ward) throw new Error('Chọn tỉnh, phường/xã và xác nhận địa chỉ xuất phát.');
      await workflowClient.updateChefProfile({...form,serviceArea:`${province}|${coords.lat.toFixed(5)}|${coords.lon.toFixed(5)}|${radius}|${ward}|${coords.proof}`,displayName:form.displayName.trim()});setSaved(true);}catch(e){setError(e instanceof Error?e.message:'Không thể lưu.');}finally{setBusy(false);}}
  return <SellerWorkspace title="Hồ sơ đầu bếp" description="Thông tin được công khai với khách hàng sau khi hồ sơ được phê duyệt.">
    <form className="card stack" onSubmit={e=>void save(e)}>
      {loading&&<p role="status" className="muted">Đang tải hồ sơ đầu bếp...</p>}
      {error&&<p role="alert" className="notice warm">{error}</p>}{saved&&<p role="status" className="notice">Đã lưu hồ sơ đầu bếp.</p>}
      <label className="field">Tên hiển thị<input required minLength={1} maxLength={255} value={form.displayName} onChange={e=>setForm({...form,displayName:e.target.value})}/></label>
      <label className="field">Giới thiệu<textarea rows={5} maxLength={2000} value={form.bio} onChange={e=>setForm({...form,bio:e.target.value})}/></label>
      <fieldset className="field"><legend>Vùng nhận nấu theo khoảng cách</legend>
        <p className="small muted">Chef chọn một điểm xuất phát tương đối (không công khai tọa độ) và bán kính phục vụ. Hệ thống sẽ loại yêu cầu ở ngoài bán kính.</p>
        <label className="field">Tỉnh/thành xuất phát<select required value={province} onChange={e=>{setProvince(e.target.value);setWard('');setCoords(null);}}>
          <option value="">Chọn tỉnh/thành</option>{PROVINCES.map(p=><option value={p.code} key={p.code}>{p.name}</option>)}
        </select></label>
        <WardSelect province={province} ward={ward} onChange={value=>{setWard(value);setCoords(null);}}/>
        <label className="field">Địa điểm xuất phát để ước tính khoảng cách<input minLength={8} maxLength={250} value={startAddress} onChange={e=>{setStartAddress(e.target.value);setCoords(null);}}
          placeholder="Ví dụ: phường Trấn Biên, Biên Hòa" /></label>
        <LocationSearch province={province} ward={ward} address={startAddress} chosen={coords} onChoose={setCoords}/>
        <label className="field">Bán kính nhận đơn<select value={radius} onChange={e=>setRadius(Number(e.target.value))}>
          {RADII.map(km=><option value={km} key={km}>{km} km</option>)}
        </select></label>
      </fieldset>
      <label className="field">Số điện thoại nghề nghiệp<input maxLength={30} value={form.phone} onChange={e=>setForm({...form,phone:e.target.value})}/></label>
      <button className="btn" disabled={busy||loading}>{busy?'Đang lưu…':'Lưu hồ sơ đầu bếp'}</button>
      <Link to={ROUTES.ACCOUNT}>Cập nhật ảnh đại diện, tên tài khoản và mật khẩu</Link>
    </form>
  </SellerWorkspace>;
}
