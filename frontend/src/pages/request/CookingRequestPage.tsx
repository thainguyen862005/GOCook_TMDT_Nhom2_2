import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { publicContentClient } from '../../clients/publicContentClient';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { CookingRequestInput } from '../../types/publicContent';

export function CookingRequestPage() {
  useDocumentTitle('Tạo yêu cầu nấu ăn | GO Cook');
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState('');
  const [error, setError] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
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
    };
    if (input.budgetMin > input.budgetMax || input.adults < 1 || input.children < 0) {
      setError('Vui lòng kiểm tra số người và khoảng ngân sách.'); return;
    }
    setBusy(true); setError(''); setCode('');
    try { const saved = await publicContentClient.request(input, file); setCode(saved.requestCode); setFile(null); formElement.reset(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Không gửi được yêu cầu.'); }
    finally { setBusy(false); }
  }
  return <div className="container">
    <div className="page-heading"><nav className="breadcrumb"><Link to="/">Trang chủ</Link><span>/</span><span>Tạo yêu cầu</span></nav>
      <h1>Một bữa ăn đúng ý bạn</h1><p className="lead">Gửi mong muốn của gia đình; yêu cầu sẽ được lưu để Admin tiếp nhận, chưa phải đơn hàng hay thanh toán.</p></div>
    {code ? <section className="card stack"><h2>Đã tiếp nhận yêu cầu</h2>
      <p role="status">Mã yêu cầu: <strong>{code}</strong>. Bộ phận vận hành sẽ xử lý trong hệ thống.</p>
      <p>Yêu cầu chưa phải là báo giá, đặt lịch hay xác nhận đầu bếp.</p><button className="btn secondary" onClick={() => setCode('')}>Tạo yêu cầu khác</button>
    </section> : <form className="split" onSubmit={submit}>
      <div className="stack">
        <section className="card stack"><h2>1. Mong muốn của gia đình</h2>
          <label className="field">Món ăn mong muốn<textarea name="desiredDishes" required maxLength={3000} rows={4} placeholder="Ví dụ: cá kho, canh chua và rau xào…" /></label>
          <label className="field">Phong cách<select name="cuisine" required>
            <option>Miền Nam</option><option>Miền Bắc</option><option>Miền Trung</option><option>Món chay</option><option>Khác</option>
          </select></label>
          <div className="grid grid-2">
            <label className="field">Người lớn<input type="number" name="adults" min={1} max={100} defaultValue={4} required /></label>
            <label className="field">Trẻ em<input type="number" name="children" min={0} max={100} defaultValue={0} required /></label>
          </div>
          <label className="field">Dị ứng hoặc thực phẩm cần tránh<textarea name="avoid" maxLength={2000} rows={3} placeholder="Ghi rõ nếu có" /></label>
          <label className="field">Mức độ cay<select name="spice"><option>Không cay</option><option>Ít cay</option><option>Vừa</option><option>Cay</option></select></label>
        </section>
        <section className="card stack"><h2>2. Thời gian và địa chỉ</h2>
          <label className="field">Ngày dùng bữa<input type="date" name="mealDate" required min={new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)} /></label>
          <label className="field">Bữa ăn<select name="mealTime"><option>Bữa trưa</option><option>Bữa tối</option></select></label>
          <label className="field">Địa chỉ nấu ăn<input type="text" name="address" required minLength={8} maxLength={500} placeholder="Số nhà, đường, quận/huyện…" /></label>
          <label className="check"><input type="checkbox" name="equipment" />Gian bếp có bếp, nồi chảo và dụng cụ cơ bản</label>
        </section>
        <section className="card stack"><h2>3. Ngân sách và ảnh tham khảo</h2>
          <div className="grid grid-2"><label className="field">Ngân sách tối thiểu (đ)<input type="number" name="budgetMin" min={1} max={1000000000} required defaultValue={400000} /></label>
            <label className="field">Ngân sách tối đa (đ)<input type="number" name="budgetMax" min={1} max={1000000000} required defaultValue={600000} /></label></div>
          <label className="field">Ảnh món ăn tham khảo (không bắt buộc)<input type="file" accept="image/jpeg,image/png,image/webp" onChange={event => chooseFile(event.currentTarget.files?.[0] || null)} /></label>
          {preview && <div className="stack-sm"><img className="admin-media-preview" src={preview} alt="Ảnh món ăn đã chọn" /><button className="btn secondary sm" type="button" onClick={() => setFile(null)}>Bỏ ảnh đã chọn</button></div>}
          <p className="small muted">Ảnh tham khảo, nếu có, được upload lên Cloudinary qua backend. Không tải ảnh chứa tài liệu hoặc thông tin cá nhân.</p>
        </section>
      </div>
      <aside className="card summary stack"><h2>Tóm tắt yêu cầu</h2>
        <p>Gửi thông tin để đội ngũ GO Cook kiểm tra và tiếp nhận.</p>
        <ul className="check-list"><li>Chưa phát sinh đơn hàng</li><li>Chưa thu tiền</li><li>Chưa xác nhận lịch của đầu bếp</li></ul>
        <button className="btn full" type="submit" disabled={busy}>{busy ? 'Đang gửi…' : 'Gửi yêu cầu nấu ăn'}</button>
        {error && <p role="alert" className="notice warm">{error}</p>}
      </aside>
    </form>}
  </div>;
}
