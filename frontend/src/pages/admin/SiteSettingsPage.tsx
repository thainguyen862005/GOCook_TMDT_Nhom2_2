import { useEffect, useState, type FormEvent } from "react";
import { AdminLayout } from "../../layouts/AdminLayout";
import { Loading } from "../../components/common/Loading";
import { SingleImageField } from "../../components/common/SingleImageField";
import { siteSettingsClient } from "../../clients/siteSettingsClient";
import type { SiteSettings } from "../../types/siteSettings";

type Slot = "hero" | "promo" | "logo";
const slots: { id: Slot; label: string; urlKey: "heroImageUrl" | "promoImageUrl" | "logoUrl" }[] = [
  { id: "hero", label: "Banner trang chủ", urlKey: "heroImageUrl" },
  { id: "promo", label: "Ảnh khuyến mãi / gợi ý", urlKey: "promoImageUrl" },
  { id: "logo", label: "Logo website (tùy chọn)", urlKey: "logoUrl" },
];
const editable: { key: keyof SiteSettings; label: string; required?: boolean; long?: boolean }[] = [
  { key: "siteName", label: "Tên website", required: true },
  { key: "tagline", label: "Khẩu hiệu", required: true },
  { key: "description", label: "Mô tả ở chân trang", required: true, long: true },
  { key: "heroTitle", label: "Tiêu đề banner", required: true },
  { key: "heroSubtitle", label: "Nội dung banner", required: true, long: true },
  { key: "heroImageAlt", label: "Mô tả ảnh cho trình đọc màn hình", required: true },
  { key: "contactEmail", label: "Email liên hệ" },
  { key: "contactPhone", label: "Hotline" },
  { key: "facebookUrl", label: "Facebook (URL HTTPS)" },
  { key: "instagramUrl", label: "Instagram (URL HTTPS)" },
  { key: "footerCopyright", label: "Dòng bản quyền", required: true },
];
export function AdminSiteSettingsPage() {
  const [data, setData] = useState<SiteSettings | null>(null);
  const [files, setFiles] = useState<Partial<Record<Slot, File>>>({});
  const [clear, setClear] = useState<Partial<Record<Slot, boolean>>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setError("");
    siteSettingsClient.admin().then(value => { if (active) setData(value); })
      .catch(err => { if (active) setError(err instanceof Error ? err.message : "Không thể tải thông tin website."); });
    return () => { active = false; };
  }, [retry]);
  const update = (key: keyof SiteSettings, value: string) =>
    setData(prev => prev ? { ...prev, [key]: value } : prev);
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!data || busy) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const saved = await siteSettingsClient.save(data, files, clear);
      setData(saved); setFiles({}); setClear({});
      setNotice("Đã cập nhật website. Tải lại trang chủ để kiểm tra giao diện.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể lưu website.");
    } finally { setBusy(false); }
  }
  return <AdminLayout title="Giao diện & nội dung website" lead="Chỉnh sửa banner, logo, thông tin liên hệ và nội dung hiển thị công khai. Ảnh được gửi lên Cloudinary qua backend.">
    {error && <p className="notice warm" role="alert">{error} <button className="btn secondary sm" type="button" onClick={() => setRetry(x => x + 1)}>Tải lại</button></p>}
    {notice && <p className="notice" role="status">{notice}</p>}
    {!data ? <Loading /> : <form className="stack" onSubmit={save}>
      <fieldset disabled={busy} className="card stack">
        <h2>Thông tin website</h2>
        {editable.map(({ key, label, required, long }) => <label className="field" key={key}>
          {label} {long ? <textarea rows={3} required={required} value={String(data[key] ?? "")} onChange={e => update(key, e.target.value)} />
            : <input required={required} value={String(data[key] ?? "")} onChange={e => update(key, e.target.value)} />}
        </label>)}
      </fieldset>
      <fieldset disabled={busy} className="card stack">
        <h2>Hình ảnh website</h2>
        <p className="small muted">Ảnh đã lưu sẽ được thay khi bấm “Lưu thay đổi”. Nếu không chọn ảnh mới, ảnh hiện tại được giữ nguyên.</p>
        {slots.map(slot => <div key={slot.id} className="stack-sm admin-image-slot">
          <h3>{slot.label}</h3>
          {!clear[slot.id] && data[slot.urlKey] && <img className="admin-media-preview" src={data[slot.urlKey]} alt={slot.label} />}
          <SingleImageField title="Chọn ảnh mới" value={files[slot.id] ?? null} disabled={busy}
            onChange={file => {
              setFiles(prev => ({ ...prev, [slot.id]: file ?? undefined }));
              if (file) setClear(prev => ({ ...prev, [slot.id]: false }));
            }} />
          <label className="small"><input type="checkbox" checked={Boolean(clear[slot.id])}
            onChange={e => {
              setClear(prev => ({ ...prev, [slot.id]: e.target.checked }));
              if (e.target.checked) setFiles(prev => ({ ...prev, [slot.id]: undefined }));
            }} /> {slot.id === "hero" ? "Khôi phục banner mặc định" : "Xóa ảnh hiện tại"}</label>
        </div>)}
      </fieldset>
      <button className="btn" type="submit" disabled={busy}>{busy ? "Đang tải ảnh & lưu..." : "Lưu thay đổi"}</button>
    </form>}
  </AdminLayout>;
}
