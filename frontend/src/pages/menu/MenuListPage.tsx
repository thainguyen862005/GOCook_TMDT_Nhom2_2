import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { menuClient } from '../../clients/menuClient';
import { Pager } from '../../components/common/Pager';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { PageResponse } from '../../types/api';
import type { MenuSummary } from '../../types/menu';
import { money } from '../../util/format';

const FALLBACK_IMAGE = '/assets/meal.png';

function parsePage(value: string | null): number {
  if (!value || !/^\d+$/.test(value)) return 0;
  const page = Number(value);
  return Number.isSafeInteger(page) && page < 100_000 ? page : 0;
}

function MenuImage({ src, alt }: { src: string | null; alt: string }) {
  return (
    <img
      src={src || FALLBACK_IMAGE}
      alt={alt}
      loading="lazy"
      onError={(event) => {
        if (!event.currentTarget.src.endsWith(FALLBACK_IMAGE)) {
          event.currentTarget.src = FALLBACK_IMAGE;
        }
      }}
    />
  );
}

export function MenuListPage() {
  useDocumentTitle('Thực đơn có sẵn | GO Cook');
  const [params, setParams] = useSearchParams();
  const q = (params.get('q') || '').trim();
  const category = params.get('category') || '';
  const minPrice = params.get('minPrice') || '';
  const maxPrice = params.get('maxPrice') || '';
  const sort = params.get('sort') || 'newest';
  const page = parsePage(params.get('page'));
  const [menus, setMenus] = useState<PageResponse<MenuSummary> | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    menuClient.categories(controller.signal)
      .then((items) => { if (!controller.signal.aborted) setCategories(items); })
      .catch(() => { /* Catalog still works if category suggestions cannot load. */ });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    menuClient.list({ q, category, minPrice, maxPrice, sort, page, size: 12 }, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setMenus(result);
          setLoading(false);
        }
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) {
          setError(reason instanceof Error ? reason.message : 'Không thể tải danh sách thực đơn.');
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [q, category, minPrice, maxPrice, sort, page, retry]);

  useEffect(() => {
    if (!menus || loading || error || page === 0) return;
    if (menus.totalPages === 0 || page >= menus.totalPages) {
      const next = new URLSearchParams(params);
      next.set('page', String(Math.max(0, menus.totalPages - 1)));
      setParams(next, { replace: true });
    }
  }, [menus, loading, error, page, params, setParams]);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const key of ['q', 'category', 'minPrice', 'maxPrice', 'sort']) {
      const value = String(form.get(key) || '').trim();
      if (value && !(key === 'sort' && value === 'newest')) next.set(key, value);
    }
    next.set('page', '0');
    setParams(next);
  }

  function changePage(nextPage: number) {
    const next = new URLSearchParams(params);
    next.set('page', String(nextPage));
    setParams(next);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  return (
    <div className="container">
      <div className="page-heading">
        <nav className="breadcrumb" aria-label="Đường dẫn">
          <Link to={ROUTES.HOME}>Trang chủ</Link><span>/</span><span>Thực đơn</span>
        </nav>
        <h1>Thực đơn cho mọi bữa sum vầy</h1>
        <p className="lead">Chọn vị quen thuộc. Để đầu bếp lo phần còn lại.</p>
      </div>
      <div className="notice row">Giá theo người · Bao gồm nguyên liệu, công nấu và dọn dẹp</div>
      <div className="section workspace">
        <form key={`${q}|${category}|${minPrice}|${maxPrice}|${sort}`}
          className="card filter" aria-label="Tìm và lọc thực đơn" onSubmit={applyFilters}>
          <h3>Bộ lọc thực đơn</h3>
          <label className="field">Tên hoặc món ăn
            <input type="search" name="q" maxLength={100} defaultValue={q} placeholder="Ví dụ: canh chua" />
          </label>
          <label className="field">Danh mục
            <select name="category" defaultValue={category}>
              <option value="">Tất cả danh mục</option>
              {[...new Set([...categories, ...(category ? [category] : [])])].map((item) => (
                <option key={item} value={item}>{item}</option>
              ))}
            </select>
          </label>
          <fieldset>
            <legend>Giá mỗi người (VNĐ)</legend>
            <label className="field">Từ
              <input name="minPrice" type="number" min={0} step={1000} defaultValue={minPrice} placeholder="0" />
            </label>
            <label className="field">Đến
              <input name="maxPrice" type="number" min={0} step={1000} defaultValue={maxPrice} placeholder="Không giới hạn" />
            </label>
          </fieldset>
          <label className="field">Sắp xếp
            <select name="sort" defaultValue={sort}>
              <option value="newest">Mới cập nhật</option>
              <option value="price_asc">Giá tăng dần</option>
              <option value="price_desc">Giá giảm dần</option>
              <option value="name">Tên A–Z</option>
            </select>
          </label>
          <button className="btn full" type="submit">Áp dụng bộ lọc</button>
          <Link className="btn secondary full" to={ROUTES.MENUS}>Xóa bộ lọc</Link>
        </form>
        <section aria-live="polite">
          <div className="toolbar">
            <strong>
              {loading ? 'Đang tải thực đơn…' : error ? 'Không thể tải thực đơn'
                : `${menus?.totalElements ?? 0} thực đơn phù hợp`}
            </strong>
          </div>
          {loading ? <p role="status">Đang tải thực đơn từ hệ thống…</p>
            : error ? (
              <div className="card stack">
                <p role="alert">{error}</p>
                <button className="btn secondary" type="button" onClick={() => setRetry((value) => value + 1)}>
                  Thử lại
                </button>
              </div>
            ) : !menus || menus.content.length === 0 ? (
              <div className="card stack">
                <p>Chưa có thực đơn công khai phù hợp. Hãy thử một bộ lọc khác.</p>
                <Link className="btn secondary" to={ROUTES.MENUS}>Xem tất cả thực đơn</Link>
              </div>
            ) : (
              <>
                <div className="grid grid-2">
                  {menus.content.map((menu) => {
                    const url = `${ROUTES.MENU_DETAIL}?id=${menu.id}`;
                    const dishes = menu.dishItems?.split('\n').map((item) => item.trim()).filter(Boolean) || [];
                    return (
                      <article className="card media-card" key={menu.id}>
                        <Link to={url} className="media" aria-label={`Xem ${menu.name}`}>
                          <MenuImage src={menu.imageUrl} alt={menu.name} />
                          <span className="badge">{menu.category}</span>
                        </Link>
                        <div className="card-body">
                          <div className="between">
                            <span className="small muted">{menu.sellerName || 'Đầu bếp GO Cook'}</span>
                            <span className="small muted">{menu.minGuests}–{menu.maxGuests} người</span>
                          </div>
                          <h3><Link to={url}>{menu.name}</Link></h3>
                          <p className="small muted">{dishes.slice(0, 4).join(' · ') || 'Thực đơn nấu tại nhà'}</p>
                          <div className="between">
                            <span className="price">{money(menu.unitPrice)}<small> / người</small></span>
                          </div>
                          <Link className="btn secondary full" to={url}>Xem thực đơn</Link>
                        </div>
                      </article>
                    );
                  })}
                </div>
                <div className="section">
                  <Pager data={menus} onPage={changePage} />
                </div>
              </>
            )}
          <div className="notice warm section">
            Bạn có khẩu vị riêng? <Link className="text-link" to={ROUTES.MATCHING}>Đặt theo nhu cầu →</Link>
          </div>
        </section>
      </div>
    </div>
  );
}
