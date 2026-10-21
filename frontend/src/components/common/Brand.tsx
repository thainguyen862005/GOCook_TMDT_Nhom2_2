import { Link } from 'react-router-dom';
import { useSiteSettings } from '../../contexts/SiteSettingsContext';

export function Brand({ admin = false }: { admin?: boolean }) {
  const site = useSiteSettings();
  return (
    <Link className="brand" to="/" aria-label={`${site.siteName} — Trang chủ`}>
      <span className="brand-mark">
        {site.logoUrl ? <img src={site.logoUrl} alt="" style={{ width: 30, height: 30, objectFit: "contain" }} /> : <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M7 14a4 4 0 0 1-2-7 4 4 0 0 1 7-2 4 4 0 0 1 7 2 4 4 0 0 1-2 7v6H7z" />
          <path d="M7 16h10M10 11v2m4-2v2" />
        </svg>}
      </span>
      <span>
        <span className="brand-name">{site.siteName}</span>
        <small>{admin ? 'QUẢN TRỊ HỆ THỐNG' : site.tagline}</small>
      </span>
    </Link>
  );
}
