import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ChefCard } from '../../components/chef/ChefCard';
import { publicContentClient } from '../../clients/publicContentClient';
import { menuClient } from '../../clients/menuClient';
import { useSiteSettings } from '../../contexts/SiteSettingsContext';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { ChefSummary } from '../../types/publicContent';
import type { MenuSummary } from '../../types/menu';
import { money } from '../../util/format';

export function HomePage() {
  const site = useSiteSettings();
  const navigate = useNavigate();
  useDocumentTitle(`${site.tagline} | ${site.siteName}`);
  const [chefs, setChefs] = useState<ChefSummary[]>([]);
  const [menus, setMenus] = useState<MenuSummary[]>([]);
  const [areas, setAreas] = useState<string[]>([]);
  const [chefError, setChefError] = useState(false);
  const [menuError, setMenuError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setLoading(true);
    Promise.allSettled([
      publicContentClient.chefs({ page: 0, size: 3, sort: 'rating' }, abort.signal),
      menuClient.list({ page: 0, size: 3 }, abort.signal),
      publicContentClient.areas(abort.signal),
    ]).then(([chefResult, menuResult, areaResult]) => {
      if (abort.signal.aborted) return;
      if (chefResult.status === 'fulfilled') { setChefs(chefResult.value.content); setChefError(false); }
      else setChefError(true);
      if (menuResult.status === 'fulfilled') { setMenus(menuResult.value.content); setMenuError(false); }
      else setMenuError(true);
      if (areaResult.status === 'fulfilled') setAreas(areaResult.value);
      setLoading(false);
    });
    return () => abort.abort();
  }, [retry]);
  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const area = String(new FormData(event.currentTarget).get('area') || '').trim();
    const params = new URLSearchParams();
    if (area) params.set('area', area);
    navigate(`${ROUTES.CHEFS}?${params}`);
  }
  return <>
    <section className="hero"><div className="container">
      <div className="hero-grid">
        <div className="hero-copy stack">
          <span className="badge">BỮA CƠM NHÀ · TRỌN VẸN YÊU THƯƠNG</span>
          <h1>{site.heroTitle}</h1><p className="lead">{site.heroSubtitle}</p>
          <div className="row"><Link className="btn" to={ROUTES.CHEFS}>Khám phá đầu bếp →</Link><Link className="btn secondary" to={ROUTES.MENUS}>Xem thực đơn</Link></div>
          <div className="trust-row"><span>Đầu bếp nấu tại nhà</span><span>Thực đơn rõ ràng</span><span>Tìm kiếm theo khu vực</span></div>
        </div>
        <div className="hero-photo">
          <img src={site.heroImageUrl} alt={site.heroImageAlt} fetchPriority="high" onError={event => {
            if (!event.currentTarget.src.endsWith('/assets/hero.png')) event.currentTarget.src = '/assets/hero.png';
          }} />
          {chefs[0] && <div className="float-card top"><img className="avatar" src={chefs[0].avatarUrl || '/assets/chef.png'} alt={chefs[0].displayName} /><div>
            <strong>{chefs[0].displayName}</strong><p className="small muted">{chefs[0].serviceArea || 'Đầu bếp đã xác minh'}</p></div></div>}
          {menus[0] && <div className="float-card"><div><p className="eyebrow">Thực đơn nổi bật</p><strong>{menus[0].name}</strong></div>
            <span className="price">{money(menus[0].unitPrice)}<small> / người</small></span></div>}
        </div>
      </div>
      <form className="search-bar" onSubmit={search}>
        <label className="field">Khu vực phục vụ<select name="area" defaultValue=""><option value="">Tất cả khu vực</option>
          {areas.map(area => <option key={area} value={area}>{area}</option>)}
        </select></label>
        <div className="field"><strong>Đầu bếp tại gia</strong><p className="small muted">Tìm từ danh sách đầu bếp đã xác minh.</p></div>
        <button className="btn" type="submit">Tìm đầu bếp →</button>
      </form>
    </div></section>
    <section className="container section stack"><div className="section-head"><div><p className="eyebrow">Gian bếp đang phục vụ</p><h2>Khám phá đầu bếp</h2></div>
      <Link className="text-link" to={ROUTES.CHEFS}>Xem tất cả →</Link></div>
      {loading ? <p role="status">Đang tải đầu bếp…</p> : chefError ? <div role="alert" className="notice">Chưa tải được đầu bếp. <button className="btn secondary sm" onClick={() => setRetry(x => x + 1)}>Tải lại</button></div>
        : chefs.length ? <div className="grid grid-3">{chefs.map(chef => <ChefCard key={chef.id} chef={chef} />)}</div>
          : <p>Chưa có đầu bếp đã xác minh để giới thiệu.</p>}
    </section>
    <section className="container section stack"><div className="section-head"><div><p className="eyebrow">Chọn bữa ăn dễ dàng</p><h2>Thực đơn mới cập nhật</h2></div>
      <Link className="text-link" to={ROUTES.MENUS}>Khám phá thực đơn →</Link></div>
      {loading ? <p role="status">Đang tải thực đơn…</p> : menuError ? <p role="alert">Chưa tải được thực đơn. Bạn có thể mở trang Thực đơn để thử lại.</p>
        : menus.length ? <div className="grid grid-3">{menus.map(menu => <article className="card" key={menu.id}>
          <img src={menu.imageUrl || '/assets/meal.png'} loading="lazy" alt={menu.name} onError={event => {
            if (!event.currentTarget.src.endsWith('/assets/meal.png')) event.currentTarget.src = '/assets/meal.png';
          }} />
          <div className="card-body stack-sm"><span className="badge gray">{menu.category}</span><h3>{menu.name}</h3>
            <p className="small muted">{menu.minGuests}–{menu.maxGuests} người {menu.sellerName ? `· ${menu.sellerName}` : ''}</p>
            <div className="between"><strong className="price">{money(menu.unitPrice)}<small> / người</small></strong>
              <Link className="btn sm" to={`${ROUTES.MENU_DETAIL}?id=${menu.id}`}>Xem chi tiết</Link></div>
          </div></article>)}</div> : <p>Hiện chưa có thực đơn công khai.</p>}
    </section>
    <section className="container section"><div className="dark-banner"><p className="eyebrow">Bữa cơm nhà theo cách của bạn</p>
      <h2>Cần một thực đơn riêng?</h2><p>Gửi mong muốn về món ăn, số người và ngân sách để đội ngũ vận hành tiếp nhận.</p>
      <Link className="btn" to={ROUTES.REQUEST}>Tạo yêu cầu nấu ăn</Link></div></section>
  </>;
}
