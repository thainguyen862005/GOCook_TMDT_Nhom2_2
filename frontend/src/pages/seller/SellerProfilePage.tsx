import { useEffect,useState,type FormEvent } from 'react';
import { SellerWorkspace } from '../../components/common/SellerWorkspace';
import { workflowClient } from '../../clients/workflowClient';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';

export function SellerProfilePage(){
  const [form,setForm]=useState({displayName:'',bio:'',serviceArea:'',phone:''});
  const [error,setError]=useState('');const [saved,setSaved]=useState(false);const [busy,setBusy]=useState(false);
  const [loading,setLoading]=useState(true);
  useEffect(()=>{let active=true; workflowClient.chefProfile()
    .then(p=>{if(active)setForm({displayName:p.displayName||'',bio:p.bio||'',serviceArea:p.serviceArea||'',phone:p.phone||''});})
    .catch(e=>{if(active)setError(e instanceof Error?e.message:'Không tải được hồ sơ đầu bếp.');})
    .finally(()=>{if(active)setLoading(false);});
    return ()=>{active=false;};},[]);
  async function save(e:FormEvent){e.preventDefault();setBusy(true);setError('');setSaved(false);
    try{await workflowClient.updateChefProfile({...form, displayName:form.displayName.trim()});setSaved(true);}catch(e){setError(e instanceof Error?e.message:'Không thể lưu.');}finally{setBusy(false);}}
  return <SellerWorkspace title="Hồ sơ đầu bếp" description="Thông tin được công khai với khách hàng sau khi hồ sơ được phê duyệt.">
    <form className="card stack" onSubmit={e=>void save(e)}>
      {loading&&<p role="status" className="muted">Đang tải hồ sơ đầu bếp...</p>}
      {error&&<p role="alert" className="notice warm">{error}</p>}{saved&&<p role="status" className="notice">Đã lưu hồ sơ đầu bếp.</p>}
      <label className="field">Tên hiển thị<input required minLength={1} maxLength={255} value={form.displayName} onChange={e=>setForm({...form,displayName:e.target.value})}/></label>
      <label className="field">Giới thiệu<textarea rows={5} maxLength={2000} value={form.bio} onChange={e=>setForm({...form,bio:e.target.value})}/></label>
      <label className="field">Khu vực phục vụ<input maxLength={500} value={form.serviceArea} onChange={e=>setForm({...form,serviceArea:e.target.value})}/></label>
      <label className="field">Số điện thoại nghề nghiệp<input maxLength={30} value={form.phone} onChange={e=>setForm({...form,phone:e.target.value})}/></label>
      <button className="btn" disabled={busy||loading}>{busy?'Đang lưu…':'Lưu hồ sơ đầu bếp'}</button>
      <Link to={ROUTES.ACCOUNT}>Cập nhật ảnh đại diện, tên tài khoản và mật khẩu</Link>
    </form>
  </SellerWorkspace>;
}
