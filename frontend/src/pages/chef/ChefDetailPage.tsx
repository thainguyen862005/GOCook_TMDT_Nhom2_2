import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { publicContentClient } from '../../clients/publicContentClient';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { ChefDetail } from '../../types/publicContent';
import { money } from '../../util/format';

export function ChefDetailPage() {
  const [params] = useSearchParams();
  const id = Number(params.get('id'));
  const [chef, setChef] = useState<ChefDetail | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useDocumentTitle(chef ? `${chef.displayName} | GO Cook` : 'Thông tin đầu bếp | GO Cook');
  useEffect(() => {
    if (!Number.isSafeInteger(id) || id <= 0) { setError('Vui lòng chọn đầu bếp từ trang Khám phá.'); setLoading(false); return; }
    const abort = new AbortController(); setLoading(true); setError('');
    publicContentClient.chef(id, abort.signal).then(value => { if (!abort.signal.aborted) setChef(value); })
      .catch(reason => { if (!abort.signal.aborted) setError(reason instanceof Error ? reason.message : 'Không tìm thấy đầu bếp.'); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [id, retry]);
  return <div className="container">
    <div className="page-heading"><nav className="breadcrumb"><Link to="/">Trang chủ</Link><span>/</span><Link to={ROUTES.CHEFS}>Đầu bếp</Link><span>/</span><span>{chef?.displayName || 'Chi tiết'}</span></nav></div>
    {loading ? <p role="status">Đang tải gian bếp…</p> : error ? <div className="card stack"><p role="alert">{error}</p><Link to={ROUTES.CHEFS}>Trở về Khám phá đầu bếp</Link><button className="btn secondary" onClick={() => setRetry(x => x + 1)}>Thử lại</button></div> : chef && <>
      <section className="profile-cover row">
        <img className="profile-photo" src={chef.avatarUrl || '/assets/chef.png'} alt={chef.displayName}
          onError={event => { if (!event.currentTarget.src.endsWith('/assets/chef.png')) event.currentTarget.src = '/assets/chef.png'; }} />
        <div className="stack-sm"><span className="badge">Đầu bếp đã xác minh</span><h1>{chef.displayName}</h1>
          <p className="muted">{chef.serviceArea || 'Chưa cập nhật khu vực'}</p>
          {chef.rating != null && chef.rating > 0 && <p className="amber">★ {Number(chef.rating).toFixed(1)}</p>}
          {chef.bio && <p>{chef.bio}</p>}
          <p className="small muted">{chef.menuCount} thực đơn công khai</p>
          <Link className="btn" to={`${ROUTES.REQUEST}?chefId=${chef.id}`}>Gửi yêu cầu đặt đầu bếp này</Link>
        </div>
      </section>
      <section className="section stack"><div className="between"><h2>Thực đơn của đầu bếp</h2><Link className="text-link" to={ROUTES.MENUS}>Xem tất cả thực đơn →</Link></div>
        {!chef.menus.length ? <p>Đầu bếp hiện chưa có thực đơn công khai.</p> : <div className="grid grid-3">
          {chef.menus.map(menu => <article className="card" key={menu.id}>
            <img src={menu.imageUrl || '/assets/meal.png'} alt={menu.name} loading="lazy" onError={event => { if (!event.currentTarget.src.endsWith('/assets/meal.png')) event.currentTarget.src = '/assets/meal.png'; }} />
            <div className="card-body stack-sm"><span className="badge gray">{menu.category}</span><h3>{menu.name}</h3>
              <p className="small muted">{menu.minGuests}–{menu.maxGuests} người</p><div className="between"><strong className="price">{money(Number(menu.unitPrice))}<small> / người</small></strong>
              <Link className="btn sm" to={`${ROUTES.MENU_DETAIL}?id=${menu.id}`}>Xem thực đơn</Link></div>
            </div></article>)}
        </div>}
      </section>
      <div className="notice">Lịch trống và đánh giá chi tiết chỉ được hiển thị khi module Booking/Review cung cấp dữ liệu xác thực; không sử dụng lịch hoặc số đánh giá minh họa.</div>
    </>}
  </div>;
}
