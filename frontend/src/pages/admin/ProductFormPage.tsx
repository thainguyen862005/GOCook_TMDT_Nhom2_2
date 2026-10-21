import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { adminClient, adminGalleryClient, type AdminGalleryImage } from '../../clients/adminClient';
import { Loading } from '../../components/common/Loading';
import { Notice } from '../../components/common/Notice';
import { PRODUCT_LABEL } from '../../constants/labels';
import { ROUTES } from '../../constants/routes';
import { AdminLayout } from '../../layouts/AdminLayout';
import type { AdminProduct } from '../../types/admin';
import type { UiMessage } from '../../types/api';

type FormState = {
  name: string; category: string; unitPrice: string; minGuests: number; maxGuests: number;
  dishItems: string; description: string; status: string;
};
interface ImageDraft { key: string; url: string; alt: string; file?: File; }
const blank: FormState = {
  name: '', category: 'Món miền Nam', unitPrice: '', minGuests: 3, maxGuests: 8,
  dishItems: '', description: '', status: 'DRAFT',
};
const maxImages = 10;
const types = ['image/jpeg', 'image/png', 'image/webp'];

export function ProductFormPage({ edit = false }: { edit?: boolean }) {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const id = params.get('id');
  const [form, setForm] = useState<FormState>(blank);
  const [categories, setCategories] = useState<string[]>([]);
  const [sellerId, setSellerId] = useState<number | null>(null);
  const [version, setVersion] = useState<number | undefined>();
  const [images, setImages] = useState<ImageDraft[]>([]);
  const [cover, setCover] = useState('');
  const objectUrls = useRef<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<UiMessage | null>(null);
  useEffect(() => () => objectUrls.current.forEach(url => URL.revokeObjectURL(url)), []);
  useEffect(() => {
    let active = true;
    setLoading(true); setLoadFailed(false); setMessage(null);
    if (edit && (!id || !/^[1-9]\d*$/.test(id))) {
      setMessage({ text: 'Mã thực đơn không hợp lệ.' }); setLoading(false); setLoadFailed(true); return;
    }
    if (!edit) { setForm(blank); setImages([]); setCover(''); setVersion(undefined); }
    const request = edit && id
      ? Promise.all([adminClient.product(id), adminGalleryClient.images(id)])
      : Promise.all([adminClient.productOptions(), Promise.resolve([] as AdminGalleryImage[])]);
    request.then(([response, gallery]) => {
      if (!active) return;
      setCategories(response.categories ?? []);
      if ('product' in response) {
        const product = (response as { product: AdminProduct }).product;
        setVersion(product.version);
        setSellerId(product.sellerId ?? null);
        setForm({
          name: product.name ?? '', category: product.category ?? 'Món miền Nam',
          unitPrice: String(product.unitPrice ?? ''), minGuests: product.minGuests ?? 3,
          maxGuests: product.maxGuests ?? 8, dishItems: product.dishItems ?? '',
          description: product.description ?? '', status: product.status ?? 'DRAFT',
        });
        setImages(gallery.map(image => ({ key: image.key, url: image.url, alt: image.alt })));
        setCover(gallery.find(image => image.cover)?.key ?? gallery[0]?.key ?? '');
      }
    }).catch((error: Error) => {
      if (active) { setLoadFailed(true); setMessage({ text: error.message }); }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [edit, id]);

  function addImages(files: File[]) {
    if (!files.length) return;
    if (images.length + files.length > maxImages) {
      setMessage({ text: `Tối đa ${maxImages} ảnh cho mỗi thực đơn.` }); return;
    }
    if (files.some(file => !types.includes(file.type) || !file.size || file.size > 5 * 1024 * 1024)) {
      setMessage({ text: 'Chỉ nhận ảnh JPG, PNG, WebP hợp lệ, không quá 5 MB/ảnh.' }); return;
    }
    const added = files.map(file => {
      const url = URL.createObjectURL(file);
      objectUrls.current.push(url);
      return { key: `local:${crypto.randomUUID()}`, url, alt: file.name, file };
    });
    setImages(current => [...current, ...added]);
    setCover(current => current || added[0]?.key || '');
    setMessage(null);
  }
  function move(index: number, delta: number) {
    setImages(current => {
      const copy = [...current];
      [copy[index], copy[index + delta]] = [copy[index + delta], copy[index]];
      return copy;
    });
  }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || loading || loadFailed) return;
    const price = Number(form.unitPrice);
    if (!form.name.trim() || form.name.trim().length > 255 || !form.category.trim()) {
      setMessage({ text: 'Vui lòng nhập tên và danh mục hợp lệ.' }); return;
    }
    if (!form.unitPrice.trim() || !Number.isFinite(price) || price < 0 ||
        price > 999999999999.99 || !/^\d+(?:\.\d{1,2})?$/.test(form.unitPrice)) {
      setMessage({ text: 'Giá phải hợp lệ và có tối đa 2 chữ số thập phân.' }); return;
    }
    if (!Number.isInteger(form.minGuests) || !Number.isInteger(form.maxGuests) ||
        form.minGuests < 1 || form.maxGuests > 100 || form.maxGuests < form.minGuests) {
      setMessage({ text: 'Số khách không hợp lệ (1–100).' }); return;
    }
    if (form.status === 'PUBLISHED' && (!images.length || !cover || price <= 0 ||
        !form.dishItems.trim() || sellerId == null)) {
      setMessage({ text: 'Muốn công khai thực đơn cần Chef, ít nhất 1 ảnh bìa, giá dương và danh sách món.' }); return;
    }
    const files: File[] = [];
    const keys = new Map<string, string>();
    images.forEach(image => {
      const key = image.file ? `new:${files.length}` : image.key;
      keys.set(image.key, key);
      if (image.file) files.push(image.file);
    });
    const payload: Omit<AdminProduct, 'id'> = {
      name: form.name.trim(), category: form.category.trim(), unitPrice: price,
      minGuests: form.minGuests, maxGuests: form.maxGuests, dishItems: form.dishItems,
      description: form.description, imageUrl: null, status: form.status,
    };
    setBusy(true); setMessage(null);
    try {
      await adminGalleryClient.save(edit ? id ?? undefined : undefined, {
        product: payload, version, imageKeys: images.map(image => keys.get(image.key)!),
        imageAlts: images.map(image => image.alt),
        coverKey: keys.get(cover) || '',
      }, files);
      navigate(ROUTES.ADMIN_PRODUCTS, { replace: true });
    } catch (error) {
      setMessage({ text: error instanceof Error ? error.message : 'Không thể lưu thực đơn.' });
    } finally { setBusy(false); }
  }

  return <AdminLayout title={edit ? 'Chỉnh sửa thực đơn' : 'Thêm thực đơn'}
    lead="Quản lý toàn bộ các thông tin hiển thị tại trang chi tiết, bao gồm album 1–10 ảnh Cloudinary.">
    <Notice message={message} />
    {loading ? <Loading /> : loadFailed ? <section className="card stack"><p>Không thể tải thực đơn.</p>
      <Link className="btn secondary" to={ROUTES.ADMIN_PRODUCTS}>Về danh sách</Link></section> :
    <form className="stack" onSubmit={save}>
      <fieldset className="card stack" disabled={busy}>
        {sellerId == null && <p className="small muted">Chưa có Chef phụ trách. Thực đơn chỉ được lưu nháp hoặc ẩn; Chef sẽ quản lý thực đơn của họ qua kênh đầu bếp.</p>}
        <div className="grid grid-2">
          <label className="field">Tên thực đơn *<input required maxLength={255} value={form.name}
            onChange={e => setForm({ ...form, name: e.target.value })} /></label>
          <label className="field">Danh mục *<select value={form.category}
            onChange={e => setForm({ ...form, category: e.target.value })}>
            {[...new Set(['Món miền Nam','Món miền Bắc','Món miền Trung','Món chay','Khác', ...categories])].map(value =>
              <option key={value} value={value}>{value}</option>)}</select></label>
          <label className="field">Giá/người *<input type="number" min="0" max="999999999999.99" step="0.01" required
            value={form.unitPrice} onChange={e => setForm({ ...form, unitPrice: e.target.value })} /></label>
          <label className="field">Trạng thái<select value={form.status}
            onChange={e => setForm({ ...form, status: e.target.value })}>
            {(edit && sellerId !== null ? ['DRAFT','PUBLISHED','HIDDEN'] : ['DRAFT','HIDDEN']).map(value =>
              <option key={value} value={value}>{PRODUCT_LABEL[value] || value}</option>)}</select></label>
          <label className="field">Khách tối thiểu *<input type="number" min="1" max="100" required value={form.minGuests}
            onChange={e => setForm({ ...form, minGuests: Number(e.target.value) })} /></label>
          <label className="field">Khách tối đa *<input type="number" min={form.minGuests} max="100" required value={form.maxGuests}
            onChange={e => setForm({ ...form, maxGuests: Number(e.target.value) })} /></label>
        </div>
        <label className="field">Danh sách món (mỗi món một dòng) *<textarea required rows={5} maxLength={10000}
          value={form.dishItems} onChange={e => setForm({ ...form, dishItems: e.target.value })} /></label>
        <label className="field">Mô tả chi tiết<textarea rows={4} maxLength={3000} value={form.description}
          onChange={e => setForm({ ...form, description: e.target.value })} /></label>
        {sellerId && <p className="small muted">Chef ID: {sellerId}</p>}
      </fieldset>
      <fieldset className="card stack" disabled={busy}>
        <h2>Album ảnh thực đơn</h2>
        <p className="small muted">Trang chi tiết hiển thị toàn bộ ảnh đã lưu theo thứ tự ở đây. Xuất bản cần tối thiểu 1 ảnh; tối đa 10 ảnh, chọn ảnh bìa. JPG/PNG/WebP, tối đa 5 MB/ảnh. Ảnh chỉ được tải lên Cloudinary khi bấm lưu.</p>
        <label className="field">Chọn ảnh mới
          <input type="file" accept={types.join(',')} multiple onChange={event => {
            addImages(Array.from(event.target.files ?? [])); event.target.value = '';
          }} />
        </label>
        <div className="admin-gallery-grid">
          {images.map((image, index) => <article className="admin-gallery-item" key={image.key}>
            <img src={image.url} alt={image.alt || form.name} />
            <label className="field small">Mô tả ảnh (alt)
              <input value={image.alt} maxLength={255} onChange={e =>
                setImages(current => current.map(item => item.key === image.key ? { ...item, alt: e.target.value } : item))} />
            </label>
            <label className="small"><input type="radio" name="admin-cover" checked={cover === image.key}
              onChange={() => setCover(image.key)} /> Ảnh bìa</label>
            <div className="row">
              <button type="button" className="btn secondary sm" disabled={index === 0} onClick={() => move(index,-1)} aria-label="Di chuyển ảnh lên">←</button>
              <button type="button" className="btn secondary sm" disabled={index === images.length - 1} onClick={() => move(index,1)} aria-label="Di chuyển ảnh xuống">→</button>
              <button type="button" className="btn ghost sm" onClick={() => {
                setImages(old => old.filter(item => item.key !== image.key));
                if (cover === image.key) setCover(images.find(item => item.key !== image.key)?.key || '');
              }}>Xóa</button>
            </div>
          </article>)}
        </div>
        {!images.length && <p className="muted">Chưa có ảnh. Chọn ít nhất 1 ảnh trước khi xuất bản.</p>}
      </fieldset>
      <div className="row"><button className="btn" type="submit" disabled={busy}>{busy ? 'Đang lưu và tải ảnh lên Cloudinary…' : 'Lưu thực đơn'}</button>
        <Link className="btn secondary" to={ROUTES.ADMIN_PRODUCTS}>Hủy</Link></div>
    </form>}
  </AdminLayout>;
}
export function AdminProductNewPage() { return <ProductFormPage />; }
export function AdminProductEditPage() { return <ProductFormPage edit />; }
