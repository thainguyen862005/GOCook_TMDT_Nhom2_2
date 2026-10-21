import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { menuClient } from '../../clients/menuClient';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { MenuDetail } from '../../types/menu';
import { ROUTES } from '../../constants/routes';

/**
 * Route: /menu-detail?id={productId}
 * Migrated from the former JSP demo markup into a dedicated React page.
 * Keep future business logic in hooks/clients for this domain, not inside the router.
 */
export function MenuDetailPage() {
  const [params] = useSearchParams();
  const id = params.get('id');
  return <MenuDetail key={id} id={id} />;
}

function MenuDetail({ id }: { id: string | null }) {
  const [data, setData] = useState<MenuDetail | null>(null);
  const [error, setError] = useState('');
  useDocumentTitle(data ? `${data.name} | GO Cook` : 'Thực đơn | GO Cook');

  useEffect(() => {
    if (!id || !/^\d+$/.test(id)) {
      setError('Không tìm thấy thực đơn. Vui lòng chọn một thực đơn từ danh sách.');
      return;
    }
    const controller = new AbortController();
    setData(null);
    setError('');
    menuClient
      .get(id, controller.signal)
      .then((result) => { if (!controller.signal.aborted) setData(result); })
      .catch((err) => { if (!controller.signal.aborted) setError(err?.message || 'Không thể tải thực đơn. Vui lòng thử lại.'); });
    return () => controller.abort();
  }, [id]);

  if (error) {
    return (
      <div className="container">
        <div className="page-heading">
          <p className="notice warm" role="alert">{error}</p>
          <a className="btn secondary" href={ROUTES.MENUS}>{'← Xem danh sách thực đơn'}</a>
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="container">
        <div className="page-heading">
          <p className="muted" role="status">{'Đang tải thực đơn…'}</p>
        </div>
      </div>
    );
  }

  const coverImage = data.images.find((img) => img.isCover);
  const otherImages = data.images.filter((img) => !img.isCover);
  const dishes = data.dishItems
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
  const unitPrice = Number(data.unitPrice).toLocaleString('vi-VN');

  return (
    <>
      <div className={'container'}>
        <div className={'page-heading'}>
          <nav className={'breadcrumb'} aria-label={'Đường dẫn'}>
            <a href={'/'}>{' Trang chủ'}</a>
            <span>{'/'}</span>
            <a href={ROUTES.MENUS}>{'Thực đơn'}</a>
            <span>{'/'}</span>
            <span>{data.name}</span>
          </nav>
          <h1>{data.name}</h1>
          {data.description && (
            <p className={'lead'}>{data.description}</p>
          )}
        </div>
        <div className={'gallery menu-gallery'} aria-label="Bộ ảnh chi tiết thực đơn">
          {coverImage ? (
            <img
              src={coverImage.url}
              alt={coverImage.name || data.name}
              fetchPriority={'high'}
            />
          ) : data.imageUrl ? (
            <img src={data.imageUrl} alt={data.name} fetchPriority={'high'} />
          ) : null}
          {otherImages.map((img) => (
            <img key={img.id} src={img.url} alt={img.name || data.name} loading={'lazy'} />
          ))}
        </div>
        <div className={'split'}>
          <div className={'stack'}>
            <section className={'card detail-section'}>
              <div className={'row'}>
                <span className={'badge'}>{data.category}</span>
                <span className={'badge gray'}>{`${data.minGuests}–${data.maxGuests} người`}</span>
              </div>
              <div className={'section'}>
                <h2>{'Thực đơn gồm những gì?'}</h2>
                <ol className={'dish-list'}>
                  {dishes.map((dish, index) => (
                    <li key={index}>
                      <span className={'dish-number'}>{String(index + 1).padStart(2, '0')}</span>
                      <div>
                        <h4>{dish}</h4>
                      </div>
                    </li>
                  ))}
                </ol>
              </div>
              <p className={'notice'}>{'Đã bao gồm nguyên liệu, công nấu và dọn bếp.'}</p>
            </section>
            {data.sellerName && (
              <section className={'card row'}>
                <img className={'avatar large'} src={'/assets/chef.png'} alt={'Ảnh đầu bếp'} loading={'lazy'} />
                <div>
                  <p className={'eyebrow'}>{'Người nấu bữa cơm của bạn'}</p>
                  <h3>{data.sellerName}</h3>
                  <a className={'text-link'} href={'/chefs'}>{'Xem danh sách đầu bếp →'}</a>
                </div>
              </section>
            )}
          </div>
          <aside className={'card summary stack'}>
            <div>
              <p className={'eyebrow'}>{'Chọn khẩu phần & lịch'}</p>
              <h3>{'Đặt thực đơn này'}</h3>
              <div className={'price'}>
                {`${unitPrice}đ`}
                <small>{' / người'}</small>
              </div>
            </div>
            <form className={'stack'}>
              <label className={'field'} htmlFor={'diners'}>
                {'Số người ăn'}
                <input
                  id={'diners'}
                  name={'diners'}
                  type={'number'}
                  defaultValue={String(data.minGuests)}
                  min={String(data.minGuests)}
                  max={String(data.maxGuests)}
                />
              </label>
              <label className={'field'} htmlFor={'cook-date'}>
                {'Ngày nấu'}
                <input id={'cook-date'} name={'cook-date'} type={'date'} defaultValue={''} placeholder={''} />
              </label>
              <label className={'field'} htmlFor={'cook-slot'}>
                {'Khung giờ'}
                <select id={'cook-slot'} name={'cook-slot'}>
                  <option>{'Bữa tối · 18:30'}</option>
                  <option>{'Bữa trưa · 11:30'}</option>
                </select>
              </label>
              <div className={'soft'}>
                <div className={'summary-line'}>
                  <span>{`Khẩu phần mẫu · ${data.minGuests} người`}</span>
                  <strong>{`${(Number(data.unitPrice) * data.minGuests).toLocaleString('vi-VN')}đ`}</strong>
                </div>
                <div className={'summary-line'}>
                  <span>{'Nguyên liệu & dọn bếp'}</span>
                  <span className={'green'}>{'Đã gồm'}</span>
                </div>
              </div>
              <button
                className={'btn'}
                type={'button'}
                data-action={'add-to-cart'}
                disabled
                title={'Chức năng sẽ được tích hợp ở giai đoạn tiếp theo'}
              >
                {'Thêm vào giỏ hàng'}
              </button>
            </form>
          </aside>
        </div>
      </div>
    </>
  );
}
