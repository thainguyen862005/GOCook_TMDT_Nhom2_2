import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { recommendationClient } from '../../clients/recommendationClient';
import { ROUTES } from '../../constants/routes';
import type { RecommendedMenu } from '../../types/recommendation';
import { money } from '../../util/format';
import '../../styles/recommendations.css';

export function SimilarMenusSection({ menuId }: { menuId: number }) {
  const [menus, setMenus] = useState<RecommendedMenu[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const abort = new AbortController();
    setStatus('loading');
    setMenus([]);
    recommendationClient.similar(menuId, abort.signal)
      .then(items => { if (!abort.signal.aborted) { setMenus(items); setStatus('ready'); } })
      .catch(() => { if (!abort.signal.aborted) setStatus('error'); });
    return () => abort.abort();
  }, [menuId, retry]);

  return <section className="section stack related-menus" aria-labelledby="knn-related-title">
    <div className="section-head"><div>
      <h2 id="knn-related-title">Thực đơn tương tự</h2>
      <p className="small muted">Gợi ý theo tên, danh mục và các món ăn của thực đơn đang xem.</p>
    </div><Link className="text-link" to={ROUTES.MENUS}>Xem các thực đơn →</Link></div>

    {status === 'loading' && <p role="status">Đang tìm thực đơn tương tự…</p>}
    {status === 'error' && <div role="alert" className="notice">Chưa tải được thực đơn tương tự.{' '}
      <button type="button" className="btn secondary sm" onClick={() => setRetry(x => x + 1)}>Thử lại</button>
    </div>}
    {status === 'ready' && menus.length === 0 && <p className="small muted">Chưa có thực đơn công khai nào đủ tương đồng để giới thiệu.</p>}
    {status === 'ready' && menus.length > 0 && <>
      <div className="grid grid-3 knn-grid">{menus.map(menu => <article className="card knn-card" key={menu.id}>
        <div className="knn-photo">
          <img loading="lazy" src={menu.imageUrl || '/assets/meal.png'} alt={menu.name}
            onError={event => { if (!event.currentTarget.src.endsWith('/assets/meal.png')) event.currentTarget.src = '/assets/meal.png'; }} />
          <span className="knn-match" aria-label={`Độ tương đồng nội dung ${menu.matchPercent}%`}>{menu.matchPercent}% tương đồng</span>
        </div>
        <div className="card-body stack-sm">
          <span className="badge gray">{menu.category}</span>
          <h3 title={menu.name}>{menu.name}</h3>
          <p className="small muted">{menu.sellerName}</p>
          <div className="knn-footer">
            <strong className="price">{money(Number(menu.unitPrice))}<small> / người</small></strong>
            <Link className="btn sm" to={`${ROUTES.MENU_DETAIL}?id=${menu.id}`}>Xem thực đơn</Link>
          </div>
        </div>
      </article>)}</div>
    </>}
  </section>;
}
