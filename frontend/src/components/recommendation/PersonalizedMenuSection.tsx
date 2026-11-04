import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { recommendationClient } from '../../clients/recommendationClient';
import { useAuth } from '../../hooks/auth/useAuth';
import { ROUTES } from '../../constants/routes';
import { money } from '../../util/format';
import type { RecommendedMenu } from '../../types/recommendation';
import '../../styles/recommendations.css';

/** Guest / Chef / Admin never call the private Customer recommendation API. */
export function PersonalizedMenuSection({ variant = 'full' }: { variant?: 'full' | 'compact' }) {
  const { profile, loading, isAdmin, isSeller } = useAuth();
  const customerId = profile && !isAdmin && !isSeller ? profile.id : null;
  const [menus, setMenus] = useState<RecommendedMenu[]>([]);
  const [loadedFor, setLoadedFor] = useState<number | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (!customerId || loading) return;
    const abort = new AbortController();
    setState('loading');
    setLoadedFor(null);
    setMenus([]);
    recommendationClient.list(abort.signal).then((result) => {
      if (!abort.signal.aborted) { setMenus(result); setLoadedFor(customerId); setState('ready'); }
    }).catch(() => {
      if (!abort.signal.aborted) { setLoadedFor(customerId); setState('error'); }
    });
    return () => abort.abort();
  }, [customerId, loading, retry]);

  if (loading || isAdmin || isSeller) return null;
  if (!customerId) {
    // Make the feature discoverable without contacting the private Customer API.
    if (variant === 'compact') return null;
    return <section className="container section stack personalized-menus" aria-labelledby="knn-section-title">
      <div className="section-head"><div>
        <p className="eyebrow">Gợi ý cá nhân hóa</p>
        <h2 id="knn-section-title">Thực đơn phù hợp với bạn</h2>
      </div></div>
      <div className="card knn-guest">
        <div className="stack-sm">
          <h3>Khám phá thực đơn theo khẩu vị của bạn</h3>
          <p className="muted">Đăng nhập để xem gợi ý từ những thực đơn bạn đã mua hoặc quan tâm trong giỏ hàng.</p>
        </div>
        <Link className="btn sm" to={ROUTES.LOGIN}>Đăng nhập để nhận gợi ý</Link>
      </div>
    </section>;
  }

  // Never briefly show another customer's recommendations after an account switch.
  const fresh = loadedFor === customerId;
  return <section className={`${variant === 'compact' ? '' : 'container '}section stack personalized-menus${variant === 'compact' ? ' personalized-menus--compact' : ''}`} aria-labelledby="knn-section-title">
    <div className="section-head"><div>
      <p className="eyebrow">Gợi ý cá nhân hóa</p>
      <h2 id="knn-section-title">Thực đơn phù hợp với bạn</h2>
      <p className="small muted">{fresh && state === 'ready' && menus[0]?.basis === 'CART'
        ? 'Gợi ý từ thực đơn bạn quan tâm trong giỏ hàng (chưa phải lịch sử mua).'
        : 'Dựa trên thực đơn bạn đã đặt và hoàn tất.'}</p>
    </div><Link className="text-link" to={ROUTES.MENUS}>Xem tất cả →</Link></div>

    {(!fresh || state === 'loading') && <p role="status">Đang tính gợi ý cho bạn…</p>}
    {fresh && state === 'error' && <div className="notice" role="alert">Chưa tải được gợi ý.{' '}
      <button type="button" className="btn secondary sm" onClick={() => setRetry(n => n+1)}>Thử lại</button>
    </div>}
    {fresh && state === 'ready' && menus.length === 0 && <div className="card knn-empty">
      <p>Chưa có lịch sử mua hay món trong giỏ để cá nhân hóa. Hãy khám phá hoặc thêm thực đơn vào giỏ nhé.</p>
      <Link className="btn secondary sm" to={ROUTES.MENUS}>Khám phá thực đơn</Link>
    </div>}
    {fresh && state === 'ready' && menus.length > 0 && <>
      <div className="grid grid-3 knn-grid">{menus.map(menu => <article className="card knn-card" key={menu.id}>
        <div className="knn-photo">
          <img loading="lazy" src={menu.imageUrl || '/assets/meal.png'} alt={menu.name}
            onError={event => { if (!event.currentTarget.src.endsWith('/assets/meal.png')) event.currentTarget.src = '/assets/meal.png'; }} />
          <span className="knn-match" aria-label={`Độ tương đồng nội dung ${menu.matchPercent}%`}>
            {menu.matchPercent}% tương đồng
          </span>
        </div>
        <div className="card-body stack-sm">
          <span className="badge gray">{menu.category}</span>
          <h3 title={menu.name}>{menu.name}</h3>
          <p className="small muted" title={menu.sellerName}>{menu.sellerName}</p>
          <p className="small knn-reason">{menu.reason}</p>
          <div className="knn-footer">
            <strong className="price">{money(Number(menu.unitPrice))}<small> / người</small></strong>
            <Link className="btn sm" to={`${ROUTES.MENU_DETAIL}?id=${menu.id}`}>Chi tiết</Link>
          </div>
        </div>
      </article>)}</div>
    </>}
  </section>;
}
