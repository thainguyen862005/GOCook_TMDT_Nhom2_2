import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { ROUTES } from '../../constants/routes';

/** Search based on actual public Chef records; does not claim an unimplemented AI model. */
export function MatchingPage() {
  useDocumentTitle('Tìm đầu bếp phù hợp | GO Cook');
  const navigate = useNavigate();
  const [area, setArea] = useState('');
  const [style, setStyle] = useState('');
  const [name, setName] = useState('');
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const qs = new URLSearchParams();
    if (area.trim()) qs.set('area', area.trim());
    if (style.trim()) qs.set('category', style.trim());
    if (name.trim()) qs.set('q', name.trim());
    navigate(`${ROUTES.CHEFS}?${qs}`);
  }
  return <div className="container">
    <div className="page-heading"><nav className="breadcrumb"><Link to="/">Trang chủ</Link><span>/</span><span>Tìm theo nhu cầu</span></nav>
      <h1>Tìm đầu bếp phù hợp</h1><p className="lead">Lọc đầu bếp đã xác minh theo dữ liệu phục vụ thật; chức năng ghép cặp AI sẽ được tích hợp sau.</p></div>
    <form className="card stack" onSubmit={submit}>
      <label className="field">Khu vực<input value={area} onChange={event => setArea(event.target.value)} maxLength={100} placeholder="Ví dụ: Bình Thạnh" /></label>
      <label className="field">Phong cách / danh mục thực đơn<input value={style} onChange={event => setStyle(event.target.value)} maxLength={100} placeholder="Ví dụ: Món chay" /></label>
      <label className="field">Tên đầu bếp (tùy chọn)<input value={name} onChange={event => setName(event.target.value)} maxLength={100} /></label>
      <button className="btn" type="submit">Xem đầu bếp phù hợp</button>
    </form>
  </div>;
}
