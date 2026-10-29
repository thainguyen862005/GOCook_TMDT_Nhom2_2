import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { CookingRequestInput } from '../../types/publicContent';
import { menuClient } from '../../clients/menuClient';
import { publicContentClient } from '../../clients/publicContentClient';
import { ROUTES } from '../../constants/routes';
import { useAuth } from '../../hooks/auth/useAuth';

export function CookingRequestPage() {
  useDocumentTitle('Tạo yêu cầu nấu ăn | GO Cook');
  const { profile } = useAuth();
  const navigate=useNavigate();const [params]=useSearchParams();
  const chefId=Number(params.get('chefId'));const menuId=Number(params.get('menuId'));
  const guestsParam=Number(params.get('guests'));const dateParam=params.get('date')||'';
  const timeParam=params.get('time')||'';const areaParam=params.get('area')||'';
  const [chosenMenu,setChosenMenu]=useState<{name:string;price:number;minGuests:number}|null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [areas,setAreas]=useState<string[]>([]);
  useEffect(()=>{publicContentClient.areas().then(setAreas).catch(()=>{});},[]);
  const [preview, setPreview] = useState('');
  const [error, setError] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(()=>{ if(menuId>0 && Number.isSafeInteger(menuId))menuClient.get(String(menuId)).then(m=>setChosenMenu({name:m.name,price:Number(m.unitPrice),minGuests:m.minGuests})).catch(e=>setError(e.message)); },[menuId]);
  useEffect(() => {
    if (!file) { setPreview(''); return; }
    const url = URL.createObjectURL(file); setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  function chooseFile(next: File | null) {
    setError('');
    if (next && (!['image/jpeg', 'image/png', 'image/webp'].includes(next.type) || next.size > 5 * 1024 * 1024)) {
      setError('Ảnh JPG, PNG, WebP tối đa 5 MB.'); setFile(null); return;
    }
    setFile(next);
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    const formElement = event.currentTarget;
    const f = new FormData(event.currentTarget);
    const input: CookingRequestInput = {
      desiredDishes: String(f.get('desiredDishes') || '').trim(), cuisine: String(f.get('cuisine') || ''),
      adults: Number(f.get('adults')), children: Number(f.get('children')),
      avoid: String(f.get('avoid') || '').trim(), spice: String(f.get('spice') || ''),
      mealDate: String(f.get('mealDate') || ''), mealTime: String(f.get('mealTime') || ''),
      address: String(f.get('address') || '').trim(), equipment: f.get('equipment') === 'on',
      budgetMin: Number(f.get('budgetMin')), budgetMax: Number(f.get('budgetMax')),
      preferredChefId:chefId>0?chefId:undefined,preferredMenuId:menuId>0?menuId:undefined,
      serviceArea:String(f.get('serviceArea')||'').trim(),
    };
    if (input.budgetMin > input.budgetMax || input.adults < 1 || input.children < 0) {
      setError('Vui lòng kiểm tra số người và khoảng ngân sách.'); return;
    }
    setBusy(true); setError(''); setCode('');
    try { const saved = await publicContentClient.request(input, file); setCode(saved.requestCode); setFile(null); formElement.reset(); if(saved.id)navigate(`${ROUTES.REQUEST_DETAIL}?id=${saved.id}`); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Không gửi được yêu cầu.'); }
    finally { setBusy(false); }
  }
  return <div className="container">
    <div className="page-heading"><nav className="breadcrumb"><Link to="/">Trang chủ</Link><span>/</span><span>Tạo yêu cầu</span></nav>
      <h1>Một bữa ăn đúng ý bạn</h1><p className="lead">Gửi mong muốn để hệ thống gợi ý đầu bếp đã xác minh. Khách sẽ chọn báo giá trước khi tạo đơn hoặc thanh toán.</p></div>
    {code ? <section className="card stack"><h2>Đã tiếp nhận yêu cầu</h2>
      <p role="status">Mã yêu cầu: <strong>{code}</strong>. Bộ phận vận hành sẽ xử lý trong hệ thống.</p>
      <p>Yêu cầu chưa phát sinh thanh toán.</p><Link to={ROUTES.MY_REQUESTS}>Xem yêu cầu của tôi</Link><button className="btn secondary" onClick={() => setCode('')}>Tạo yêu cầu khác</button>
    </section> : <form className="split" onSubmit={submit}>
      <div className="stack">
        <section className="card stack"><h2>1. Mong muốn của gia đình</h2>
          {chosenMenu && <p className="notice">Bạn đang yêu cầu nấu theo thực đơn: <strong>{chosenMenu.name}</strong>. Đầu bếp nhận yêu cầu cần gửi báo giá trước khi bạn thanh toán; không áp dụng giá của người đăng thực đơn cho Chef khác.</p>}
          <label className="field">Món ăn mong muốn<textarea name="desiredDishes" defaultValue={chosenMenu?.name||''} key={chosenMenu?.name} required maxLength={3000} rows={4} placeholder="Ví dụ: cá kho, canh chua và rau xào…" /></label>
          <label className="field">Phong cách<select name="cuisine" required>
            <option>Miền Nam</option><option>Miền Bắc</option><option>Miền Trung</option><option>Món chay</option><option>Khác</option>
          </select></label>
          <div className="grid grid-2">
            <label className="field">Người lớn<input type="number" name="adults" min={1} max={100} defaultValue={guestsParam>0?guestsParam:chosenMenu?.minGuests||4} key={chosenMenu?.minGuests} required /></label>
            <label className="field">Trẻ em<input type="number" name="children" min={0} max={100} defaultValue={0} required /></label>
          </div>
          <label className="field">Dị ứng hoặc thực phẩm cần tránh<textarea name="avoid" maxLength={2000} rows={3} placeholder="Ghi rõ nếu có" /></label>
          <label className="field">Mức độ cay<select name="spice"><option>Không cay</option><option>Ít cay</option><option>Vừa</option><option>Cay</option></select></label>
        </section>
        <section className="card stack"><h2>2. Thời gian và địa chỉ</h2>
          <label className="field">Ngày dùng bữa<input type="date" name="mealDate" defaultValue={dateParam} required min={new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)} /></label>
          <label className="field">Bữa ăn<select name="mealTime" defaultValue={timeParam==='11:30'?'Bữa trưa':'Bữa tối'}><option>Bữa trưa</option><option>Bữa tối</option></select></label>
          <label className="field">Khu vực phục vụ mong muốn
            <input list="request-service-areas" name="serviceArea" maxLength={100} required defaultValue={areaParam}
              placeholder="Ví dụ: Dĩ An, Bình Dương" />
            <datalist id="request-service-areas">{areas.map(area=><option key={area} value={area} />)}</datalist>
            <span className="small muted">Hãy chọn khu vực có Chef được duyệt. Matching chỉ gợi ý Chef có hồ sơ phục vụ khu vực này và chưa bị giữ lịch.</span>
          </label>
          <label className="field">Địa chỉ nấu ăn<input type="text" name="address" key={profile?.id} defaultValue={profile?.defaultAddress || ''} required minLength={8} maxLength={500} placeholder="Số nhà, đường, quận/huyện…" /></label>
          <label className="check"><input type="checkbox" name="equipment" />Gian bếp có bếp, nồi chảo và dụng cụ cơ bản</label>
        </section>
        <section className="card stack"><h2>3. Ngân sách và ảnh tham khảo</h2>
          <div className="grid grid-2"><label className="field">Ngân sách tối thiểu (đ)<input type="number" name="budgetMin" min={1} max={1000000000} required defaultValue={chosenMenu?chosenMenu.price*chosenMenu.minGuests:400000} key={chosenMenu?.price} /></label>
            <label className="field">Ngân sách tối đa (đ)<input type="number" name="budgetMax" min={1} max={1000000000} required defaultValue={chosenMenu?chosenMenu.price*chosenMenu.minGuests:600000} key={chosenMenu?.name} /></label></div>
          <label className="field">Ảnh món ăn tham khảo (không bắt buộc)<input type="file" accept="image/jpeg,image/png,image/webp" onChange={event => chooseFile(event.currentTarget.files?.[0] || null)} /></label>
          {preview && <div className="stack-sm"><img className="admin-media-preview" src={preview} alt="Ảnh món ăn đã chọn" /><button className="btn secondary sm" type="button" onClick={() => setFile(null)}>Bỏ ảnh đã chọn</button></div>}
          <p className="small muted">Ảnh tham khảo, nếu có, được upload lên Cloudinary qua backend. Không tải ảnh chứa tài liệu hoặc thông tin cá nhân.</p>
        </section>
      </div>
      <aside className="card summary stack"><h2>Tóm tắt yêu cầu</h2>{chefId>0&&<p>Đang chọn đầu bếp #{chefId}</p>}
        <p>Sau khi gửi, Chef được ghép sẽ thấy yêu cầu trong hàng chờ và có thể chat, báo giá. Chưa có tiền được thu.</p>
        <ul className="check-list"><li>Chưa phát sinh đơn hàng</li><li>Chưa thu tiền</li><li>Chưa xác nhận lịch của đầu bếp</li></ul>
        <button className="btn full" type="submit" disabled={busy}>{busy ? 'Đang gửi…' : 'Gửi yêu cầu nấu ăn'}</button>
        {error && <p role="alert" className="notice warm">{error}</p>}
      </aside>
    </form>}
  </div>;
}
