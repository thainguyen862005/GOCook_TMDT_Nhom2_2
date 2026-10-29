import { useEffect, useState } from 'react';
import { httpBlob } from '../../clients/httpClient';

export type OrderEvidence = {
  id: number; filename: string; mimeType: string; note: string;
  evidenceType: 'INGREDIENT_RECEIPT' | 'FINISHED_MEAL';
};

function PrivatePhoto({ path, label }: { path: string; label: string }) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState(false);
  useEffect(() => {
    const abort = new AbortController();
    let blobUrl = '';
    setUrl(''); setError(false);
    void httpBlob(path, abort.signal).then(blob => {
      if (abort.signal.aborted) return;
      blobUrl = URL.createObjectURL(blob);
      setUrl(blobUrl);
    }).catch(() => { if (!abort.signal.aborted) setError(true); });
    return () => { abort.abort(); if (blobUrl) URL.revokeObjectURL(blobUrl); };
  }, [path]);
  if (error) return <p role="alert" className="small muted">Không mở được ảnh riêng tư. Vui lòng đăng nhập lại hoặc tải trang.</p>;
  if (!url) return <p className="small muted">Đang tải ảnh…</p>;
  return <a href={url} target="_blank" rel="noreferrer" aria-label={`Xem ảnh ${label}`}>
    <img src={url} alt={label} loading="lazy" className="quality-photo" />
  </a>;
}

export function OrderEvidenceGallery({ evidence, imagePath, canRemove, onRemove, busy = false }:
  { evidence: OrderEvidence[]; imagePath: (id: number) => string;
    canRemove?: (item: OrderEvidence) => boolean; onRemove?: (id: number) => void; busy?: boolean; }) {
  const groups = [
    { type: 'INGREDIENT_RECEIPT', title: 'Hóa đơn / nguồn gốc nguyên liệu', empty: 'Chưa có hóa đơn nguyên liệu.' },
    { type: 'FINISHED_MEAL', title: 'Ảnh món hoàn thành', empty: 'Chưa có ảnh món hoàn thành.' },
  ] as const;
  return <div className="quality-evidence-groups">
    {groups.map(group => <div className="stack-sm" key={group.type}>
      <h3>{group.title}</h3>
      {!evidence.some(item => item.evidenceType === group.type) && <p className="small muted">{group.empty}</p>}
      <div className="quality-evidence-grid">
        {evidence.filter(item => item.evidenceType === group.type).map(item => <article className="quality-evidence-card" key={item.id}>
          <PrivatePhoto path={imagePath(item.id)} label={`${group.title}: ${item.filename}`} />
          <p className="small"><strong>{item.note || item.filename}</strong></p>
          {canRemove?.(item) && onRemove && <button className="btn ghost sm" type="button" disabled={busy} onClick={() => onRemove(item.id)}>Xóa ảnh</button>}
        </article>)}
      </div>
    </div>)}
  </div>;
}
