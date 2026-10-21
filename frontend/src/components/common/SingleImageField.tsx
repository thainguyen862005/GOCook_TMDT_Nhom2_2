import { useEffect, useState } from "react";

const formats = ["image/jpeg", "image/png", "image/webp"];
export function SingleImageField({ title, value, onChange, disabled, required, hint }: {
  title: string; value: File | null; onChange: (file: File | null) => void;
  disabled?: boolean; required?: boolean; hint?: string;
}) {
  const [url, setUrl] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    if (!value) { setUrl(""); return; }
    const preview = URL.createObjectURL(value);
    setUrl(preview);
    return () => URL.revokeObjectURL(preview);
  }, [value]);
  return <div className="field">
    <label>{title}{required ? " *" : ""}</label>
    <input disabled={disabled} type="file" accept={formats.join(",")}
      onChange={(event) => {
        const file = event.target.files?.[0] ?? null;
        event.target.value = "";
        if (!file) return;
        if (!formats.includes(file.type) || !file.size || file.size > 5 * 1024 * 1024) {
          setError("Chỉ nhận JPG/PNG/WebP có dữ liệu, tối đa 5 MB."); return;
        }
        setError(""); onChange(file);
      }} />
    {error && <p className="notice warm" role="alert">{error}</p>}
    {url && <img className="admin-media-preview" src={url} alt={`Ảnh xem trước: ${title}`} />}
    {value && <button className="btn ghost sm" type="button" disabled={disabled} onClick={() => onChange(null)}>Bỏ ảnh mới</button>}
    <p className="small muted">{hint ?? "JPG/PNG/WebP · tối đa 5 MB · ảnh minh họa trước khi lưu"}</p>
  </div>;
}
