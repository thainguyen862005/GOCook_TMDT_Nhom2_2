import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ChefCard } from '../../components/chef/ChefCard';
import { publicContentClient } from '../../clients/publicContentClient';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { ChefSummary, PublicPage } from '../../types/publicContent';
import { provinceName } from '../../constants/provinces';

function pageValue(raw: string | null) {
  const value = Number(raw);
  return raw && /^\d+$/.test(raw) && Number.isSafeInteger(value) && value <= 100000 ? value : 0;
}

export function ChefListPage() {
  useDocumentTitle('Khám phá đầu bếp | GO Cook');
  const [params, setParams] = useSearchParams();
  const q = params.get('q') || '';
  const area = params.get('area') || '';
  const category = params.get('category') || '';
  const sort = params.get('sort') || 'rating';
  const page = pageValue(params.get('page'));
  const [data, setData] = useState<PublicPage<ChefSummary> | null>(null);
  const [areas, setAreas] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const abort = new AbortController();
    publicContentClient.areas(abort.signal).then(setAreas).catch(() => {});
    return () => abort.abort();
  }, []);
  useEffect(() => {
    const abort = new AbortController();
    setLoading(true); setError('');
    publicContentClient.chefs({ q, area, category, sort, page, size: 12 }, abort.signal)
      .then(result => { if (!abort.signal.aborted) setData(result); })
      .catch(err => { if (!abort.signal.aborted) setError(err instanceof Error ? err.message : 'Không tải được đầu bếp.'); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [q, area, category, sort, page, retry]);

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const key of ['q', 'area', 'category', 'sort']) {
      const value = String(form.get(key) || '').trim();
      if (value && !(key === 'sort' && value === 'rating')) next.set(key, value);
    }
    next.set('page', '0'); setParams(next);
  }
  const navigatePage = (next: number) => {
    const updated = new URLSearchParams(params); updated.set('page', String(next)); setParams(updated);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };
  return <div className="container">
    <div className="page-heading">
      <nav className="breadcrumb"><Link to="/">Trang chủ</Link><span>/</span><span>Khám phá đầu bếp</span></nav>
      <h1>Đầu bếp cho bữa cơm nhà bạn</h1>
      <p className="lead">Tìm người nấu phù hợp theo khu vực và những thực đơn đang phục vụ.</p>
    </div>
    <div className="workspace">
      <form key={`${q}|${area}|${category}|${sort}`} className="card filter" onSubmit={filter}>
        <h3>Tìm đầu bếp</h3>
        <label className="field">Tên đầu bếp<input type="search" maxLength={100} name="q" defaultValue={q} placeholder="Tên gian bếp hoặc đầu bếp" /></label>
        <label className="field">Khu vực phục vụ<select name="area" defaultValue={area}>
          <option value="">Tất cả khu vực</option>
          {[...new Set([...areas, ...(area ? [area] : [])])].map(item => <option key={item} value={item}>{provinceName(item)}</option>)}
        </select></label>
        <label className="field">Loại thực đơn<input name="category" maxLength={100} defaultValue={category} placeholder="Ví dụ: Món chay" /></label>
        <label className="field">Sắp xếp<select name="sort" defaultValue={sort}>
          <option value="rating">Đánh giá</option><option value="menus">Nhiều thực đơn</option><option value="name">Tên A–Z</option>
        </select></label>
        <button type="submit" className="btn full">Tìm đầu bếp</button>
        <Link className="btn secondary full" to={ROUTES.CHEFS}>Xóa bộ lọc</Link>
      </form>
      <section className="stack" aria-live="polite">
        <div className="between"><h2>Đầu bếp đang phục vụ</h2><span className="badge gray">{loading ? 'Đang tải…' : `${data?.totalElements ?? 0} kết quả`}</span></div>
        {loading ? <p role="status">Đang tìm đầu bếp…</p> : error ? <div className="card stack"><p role="alert">{error}</p><button className="btn secondary" onClick={() => setRetry(x => x + 1)}>Thử lại</button></div>
          : !data?.content.length ? <div className="card stack"><p>Chưa có đầu bếp đã xác minh phù hợp với bộ lọc.</p><Link to={ROUTES.CHEFS}>Xem tất cả</Link></div>
            : <>{data.content.map(chef => <ChefCard key={chef.id} chef={chef} />)}
              {data.totalPages > 1 && <div className="between">
                <button type="button" className="btn secondary sm" disabled={page === 0} onClick={() => navigatePage(page - 1)}>← Trước</button>
                <span className="small muted">Trang {page + 1}/{data.totalPages}</span>
                <button type="button" className="btn secondary sm" disabled={page + 1 >= data.totalPages} onClick={() => navigatePage(page + 1)}>Sau →</button>
              </div>}</>}
        <div className="notice">Chưa biết chọn ai? <Link className="text-link" to={ROUTES.MATCHING}>Tìm theo nhu cầu →</Link></div>
      </section>
    </div>
  </div>;
}
