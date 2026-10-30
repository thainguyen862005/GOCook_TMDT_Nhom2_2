import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { menuClient } from '../../clients/menuClient';
import { publicContentClient } from '../../clients/publicContentClient';
import type { ChefSummary } from '../../types/publicContent';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { MenuDetail } from '../../types/menu';
import { ROUTES } from '../../constants/routes';
import { PROVINCES, provinceList } from '../../constants/provinces';

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
  const [guests, setGuests] = useState<number | null>(null);
  const [date, setDate] = useState('');
  const [time, setTime] = useState('18:30');

  const [bookingMode, setBookingMode] = useState<'creator'|'choose'|'match'>('creator');
  const [serviceArea, setServiceArea] = useState('');
  const [candidates, setCandidates] = useState<ChefSummary[]>([]);
  const [selectedChef, setSelectedChef] = useState<number | null>(null);
  const [searching, setSearching] = useState(false);
  const [chefError, setChefError] = useState('');
  useEffect(() => {
    if (bookingMode !== 'choose' || !data || !serviceArea.trim()) { setCandidates([]); return; }
    const controller = new AbortController(); setSearching(true); setChefError('');
    publicContentClient.chefs({area:serviceArea.trim(),category:data.category,page:0,size:30,sort:'rating'},controller.signal)
      .then(result=>{ if(!controller.signal.aborted) setCandidates(result.content); })
      .catch(err=>{ if(!controller.signal.aborted) setChefError(err instanceof Error?err.message:'Không tải được đầu bếp.'); })
      .finally(()=>{ if(!controller.signal.aborted) setSearching(false); });
    return ()=>controller.abort();
  },[bookingMode, serviceArea, data?.id, data?.category]);
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
  const selectedGuests = guests ?? data.minGuests;
  const validGuests = Number.isInteger(selectedGuests) && selectedGuests >= data.minGuests && selectedGuests <= data.maxGuests;
  const unitPrice = Number(data.unitPrice).toLocaleString('vi-VN');
  const today = new Date(Date.now()-new Date().getTimezoneOffset()*60000).toISOString().slice(0,10);
  const checkoutParams = new URLSearchParams({ productId: String(data.id), guests: String(selectedGuests), date, time });
  const requestParams = new URLSearchParams({menuId:String(data.id),guests:String(selectedGuests),date,time,area:serviceArea.trim()});
  if (bookingMode==='choose' && selectedChef) requestParams.set('chefId',String(selectedChef));
  const requestedStart = date && time ? new Date(`${date}T${time}:00`).getTime() : NaN;
  const canContinue = validGuests && date >= today && Number.isFinite(requestedStart) && requestedStart > Date.now();
  const regionValid = serviceArea.trim().length > 0;

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
                  <p className={'eyebrow'}>{'Đầu bếp đăng thực đơn'}</p>
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
            <div className={'stack'}>
              <label className={'field'} htmlFor={'diners'}>
                {'Số người ăn'}
                <input
                  id={'diners'}
                  name={'diners'}
                  type={'number'}
                  value={selectedGuests}
                  onChange={event => setGuests(Number(event.target.value))}
                  required
                  min={String(data.minGuests)}
                  max={String(data.maxGuests)}
                />
              </label>
              <label className={'field'} htmlFor={'cook-date'}>
                {'Ngày nấu'}
                <input id={'cook-date'} name={'cook-date'} type={'date'} value={date} min={today} onChange={event => setDate(event.target.value)} required />
              </label>
              <label className={'field'} htmlFor={'cook-slot'}>
                {'Khung giờ'}
                <select id={'cook-slot'} name={'cook-slot'} value={time} onChange={event => setTime(event.target.value)}>
                  <option value="18:30">{'Bữa tối · 18:30'}</option>
                  <option value="11:30">{'Bữa trưa · 11:30'}</option>
                </select>
              </label>
              <div className={'soft'}>
                <div className={'summary-line'}>
                  <span>{`Khẩu phần · ${selectedGuests} người`}</span>
                  <strong>{`${(Number(data.unitPrice) * selectedGuests).toLocaleString('vi-VN')}đ`}</strong>
                </div>
                <div className={'summary-line'}>
                  <span>{'Nguyên liệu & dọn bếp'}</span>
                  <span className={'green'}>{'Đã gồm'}</span>
                </div>
              </div>
              <section className="stack" aria-label="Lựa chọn đầu bếp">
                <h4>Ai sẽ nấu thực đơn này?</h4>
                <label className="check"><input type="radio" name="chef-mode" checked={bookingMode==='creator'} onChange={()=>{setBookingMode('creator');setSelectedChef(null);}} /> Đầu bếp đăng thực đơn · giá niêm yết · thanh toán ngay</label>
                <label className="check"><input type="radio" name="chef-mode" checked={bookingMode==='choose'} onChange={()=>{setBookingMode('choose');setSelectedChef(null);}} /> Tôi muốn chọn đầu bếp khác</label>
                <label className="check"><input type="radio" name="chef-mode" checked={bookingMode==='match'} onChange={()=>{setBookingMode('match');setSelectedChef(null);}} /> Hệ thống gợi ý đầu bếp gần khu vực của tôi</label>
              </section>
              {bookingMode !== 'creator' && <section className="stack">
                <label className="field">Khu vực cần phục vụ
                  <select value={serviceArea} onChange={event=>{setServiceArea(event.target.value);setSelectedChef(null);}} required>
                    <option value="">Chọn tỉnh/thành</option>
                    {PROVINCES.map(p=><option key={p.code} value={p.code}>{p.name}</option>)}
                  </select>
                </label>
                <p className="small muted">Danh sách Chef tại đây chỉ lọc sơ bộ theo tỉnh/thành. Khi tạo yêu cầu bạn cần xác nhận vị trí nấu; backend sẽ kiểm tra bán kính phục vụ và lịch trống thực tế. Giá niêm yết chỉ áp dụng cho đầu bếp đăng thực đơn.</p>
                {bookingMode==='choose' && <div className="stack">
                  {searching&&<p role="status">Đang tìm Chef có thực đơn cùng phong cách trong khu vực…</p>}
                  {chefError&&<p role="alert" className="notice warm">{chefError}</p>}
                  {regionValid && !searching && !candidates.length && !chefError && <p className="notice warm">Chưa thấy Chef phù hợp. Bạn có thể chọn ghép tự động để Admin hỗ trợ.</p>}
                  {candidates.map(chef=><label key={chef.id} className="check">
                    <input type="radio" name="chosen-chef" checked={selectedChef===chef.id}
                      onChange={()=>setSelectedChef(chef.id)}/>
                    {chef.displayName} · {provinceList(chef.serviceArea)} · {Number(chef.rating??0).toFixed(1)}/5
                  </label>)}
                </div>}
              </section>}
              {!canContinue ? <p className="notice warm">Chọn ngày, giờ nấu trong tương lai và số khách hợp lệ để tiếp tục.</p> : bookingMode==='creator'
                ? <Link className="btn" to={`${ROUTES.CHECKOUT}?${checkoutParams}`}>Tiếp tục thanh toán với Chef đăng thực đơn</Link>
                : !regionValid || (bookingMode==='choose'&&!selectedChef)
                  ? <p className="notice warm">Chọn khu vực{bookingMode==='choose'?' và đầu bếp':''} để tiếp tục.</p>
                  : <Link className="btn" to={`${ROUTES.REQUEST}?${requestParams}`}>{bookingMode==='choose'?'Gửi yêu cầu đến đầu bếp đã chọn':'Gửi yêu cầu ghép đầu bếp'}</Link>}
              <Link className='btn secondary' to={`${ROUTES.REQUEST}?menuId=${data.id}`}>Yêu cầu chỉnh món / trao đổi riêng</Link>
            </div>
          </aside>
        </div>
      </div>
    </>
  );
}
