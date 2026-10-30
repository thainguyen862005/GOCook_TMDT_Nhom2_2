/** Shared 0-based pagination for Chef screens. The server remains the source of truth for totals. */
export function SellerPagination({ page, totalPages, onPage, disabled = false }: {
  page: number; totalPages: number; onPage: (page: number) => void; disabled?: boolean;
}) {
  if (!Number.isInteger(totalPages) || totalPages <= 1) return null;
  const current = Math.max(0, Math.min(page, totalPages - 1));
  const start = Math.max(0, Math.min(current - 2, totalPages - 5));
  const pages = Array.from({ length: Math.min(5, totalPages) }, (_, i) => start + i);
  return <nav className="chef-pagination" aria-label="Phân trang">
    <span className="small muted">Trang {current + 1} / {totalPages}</span>
    <div className="chef-pagination-actions">
      <button type="button" className="btn secondary sm" disabled={disabled || current === 0} onClick={() => onPage(current - 1)}>← Trước</button>
      {pages.map(index => <button type="button" key={index} disabled={disabled} aria-label={`Trang ${index + 1}`}
        aria-current={index === current ? 'page' : undefined}
        className={`btn sm ${index === current ? '' : 'secondary'}`} onClick={() => onPage(index)}>{index + 1}</button>)}
      <button type="button" className="btn secondary sm" disabled={disabled || current >= totalPages - 1} onClick={() => onPage(current + 1)}>Sau →</button>
    </div>
  </nav>;
}
