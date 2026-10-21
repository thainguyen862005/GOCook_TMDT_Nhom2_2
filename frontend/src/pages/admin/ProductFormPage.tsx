import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { adminClient } from '../../clients/adminClient';
import { Loading } from '../../components/common/Loading';
import { Notice } from '../../components/common/Notice';
import { PRODUCT_LABEL } from '../../constants/labels';
import { ROUTES } from '../../constants/routes';
import { AdminLayout } from '../../layouts/AdminLayout';
import type { AdminProduct } from '../../types/admin';
import type { UiMessage } from '../../types/api';

type FormState = {
  name: string; category: string; unitPrice: string; minGuests: number; maxGuests: number;
  dishItems: string; description: string; imageUrl: string; status: string;
};
const blankForm: FormState = {
  name: '', category: 'Món miền Nam', unitPrice: '', minGuests: 3, maxGuests: 8,
  dishItems: '', description: '', imageUrl: '', status: 'DRAFT',
};

export function ProductFormPage({ edit = false }: { edit?: boolean }) {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const id = params.get('id');
  const [form, setForm] = useState<FormState>(blankForm);
  const [categories, setCategories] = useState<string[]>([]);
  const [sellerId, setSellerId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<UiMessage | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setLoadFailed(false);
    setMessage(null);
    if (edit && (!id || !/^[1-9]\d*$/.test(id))) {
      setMessage({ text: 'Mã thực đơn không hợp lệ.' });
      setLoading(false);
      setLoadFailed(true);
      return;
    }
    if (!edit) setForm(blankForm);
    const request = edit && id ? adminClient.product(id) : adminClient.productOptions();
    request.then((response) => {
      if (!active) return;
      setCategories(response.categories ?? []);
      if ('product' in response) {
        const product = (response as { product: AdminProduct }).product;
        setSellerId(product.sellerId ?? null);
        setForm({
          name: product.name ?? '', category: product.category ?? 'Món miền Nam',
          unitPrice: String(product.unitPrice ?? ''), minGuests: product.minGuests ?? 3,
          maxGuests: product.maxGuests ?? 8, dishItems: product.dishItems ?? '',
          description: product.description ?? '', imageUrl: product.imageUrl ?? '',
          status: product.status ?? 'DRAFT',
        });
      }
    }).catch((error: Error) => {
      if (active) { setLoadFailed(true); setMessage({ text: error.message }); }
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [edit, id]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || loading || loadFailed) return;
    const price = Number(form.unitPrice);
    if (!form.name.trim() || form.name.trim().length > 255 || !form.category.trim()) {
      setMessage({ text: 'Vui lòng nhập tên và danh mục hợp lệ.' }); return;
    }
    if (!form.unitPrice.trim() || !Number.isFinite(price) || price < 0
        || price > 999999999999.99 || !/^\d+(?:\.\d{1,2})?$/.test(form.unitPrice)) {
      setMessage({ text: 'Giá phải hợp lệ và có tối đa 2 chữ số thập phân.' }); return;
    }
    if (!Number.isInteger(form.minGuests) || !Number.isInteger(form.maxGuests)
        || form.minGuests < 1 || form.maxGuests < form.minGuests) {
      setMessage({ text: 'Số khách không hợp lệ.' }); return;
    }
    if (form.status === 'PUBLISHED' && (sellerId == null || !form.imageUrl.trim() || price <= 0)) {
      setMessage({ text: 'Thực đơn công khai cần Chef, ảnh đại diện và giá lớn hơn 0.' }); return;
    }
    const payload: Omit<AdminProduct, 'id'> = {
      name: form.name.trim(), category: form.category.trim(), unitPrice: price,
      minGuests: form.minGuests, maxGuests: form.maxGuests,
      dishItems: form.dishItems, description: form.description,
      imageUrl: form.imageUrl, status: form.status,
    };
    setBusy(true);
    setMessage(null);
    try {
      if (edit && id) await adminClient.updateProduct(id, payload);
      else await adminClient.createProduct(payload);
      navigate(ROUTES.ADMIN_PRODUCTS, { replace: true });
    } catch (error) {
      setMessage({ text: error instanceof Error ? error.message : 'Không thể lưu thực đơn.' });
    } finally { setBusy(false); }
  }

  return (
    <AdminLayout title={edit ? 'Chỉnh sửa thực đơn' : 'Thêm thực đơn'}
      lead="Thực đơn mới phải lưu bản nháp trước; chỉ công khai sau khi gắn Chef.">
      <Notice message={message} />
      {loading ? <Loading /> : loadFailed ? (
        <section className="card stack"><p>Không thể tải thông tin thực đơn.</p>
          <Link className="btn secondary" to={ROUTES.ADMIN_PRODUCTS}>Về danh sách</Link></section>
      ) : (
        <form className="card stack" onSubmit={save}>
          {sellerId == null && <p className="small muted">Thực đơn này chưa gắn Chef; chỉ có thể để Draft/Hidden. Chức năng gắn Chef sẽ được bổ sung ở A05.</p>}
          <div className="grid grid-2">
            <label className="field">Tên thực đơn<input required maxLength={255} value={form.name} disabled={busy}
              onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
            <label className="field">Danh mục<select value={form.category} disabled={busy}
              onChange={(e) => setForm({ ...form, category: e.target.value })}>
              {[...new Set(['Món miền Nam','Món miền Bắc','Món miền Trung','Món chay','Khác', ...categories])].map((value) =>
                <option key={value} value={value}>{value}</option>)}</select></label>
            <label className="field">Giá/người<input type="number" min="0" step="0.01" required value={form.unitPrice} disabled={busy}
              onChange={(e) => setForm({ ...form, unitPrice: e.target.value })} /></label>
            <label className="field">Trạng thái<select value={form.status} disabled={busy}
              onChange={(e) => setForm({ ...form, status: e.target.value })}>
              {(edit && sellerId !== null ? ['DRAFT','PUBLISHED','HIDDEN'] : ['DRAFT','HIDDEN']).map((value) =>
                <option key={value} value={value}>{PRODUCT_LABEL[value] || value}</option>)}</select></label>
            <label className="field">Khách tối thiểu<input type="number" min="1" required value={form.minGuests} disabled={busy}
              onChange={(e) => setForm({ ...form, minGuests: Number(e.target.value) })} /></label>
            <label className="field">Khách tối đa<input type="number" min={form.minGuests} required value={form.maxGuests} disabled={busy}
              onChange={(e) => setForm({ ...form, maxGuests: Number(e.target.value) })} /></label>
          </div>
          <label className="field">Danh sách món<textarea value={form.dishItems} disabled={busy}
            onChange={(e) => setForm({ ...form, dishItems: e.target.value })} /></label>
          <label className="field">Mô tả<textarea value={form.description} disabled={busy}
            onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
          <label className="field">URL ảnh<input value={form.imageUrl} maxLength={1000} disabled={busy}
            onChange={(e) => setForm({ ...form, imageUrl: e.target.value })} placeholder="https://... hoặc /assets/meal.png" /></label>
          <div className="row"><button className="btn" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu thực đơn'}</button>
            <Link className="btn secondary" to={ROUTES.ADMIN_PRODUCTS}>Hủy</Link></div>
        </form>
      )}
    </AdminLayout>
  );
}

export function AdminProductNewPage() { return <ProductFormPage />; }
export function AdminProductEditPage() { return <ProductFormPage edit />; }
