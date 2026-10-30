import { Link } from 'react-router-dom';
import type { ChefSummary } from '../../types/publicContent';
import { ROUTES } from '../../constants/routes';
import { money } from '../../util/format';
import { provinceList } from '../../constants/provinces';

function ChefPortrait({ url, name }: { url: string | null; name: string }) {
  return <img src={url || '/assets/chef.png'} loading="lazy" alt={`Đầu bếp ${name}`}
    onError={event => { if (!event.currentTarget.src.endsWith('/assets/chef.png')) event.currentTarget.src = '/assets/chef.png'; }} />;
}

export function ChefCard({ chef, compact = false }: { chef: ChefSummary; compact?: boolean }) {
  return <article className={`card chef-card ${compact ? 'chef-card--featured' : ''}`}>
    <ChefPortrait url={chef.avatarUrl} name={chef.displayName} />
    <div className="card-body stack-sm">
      <div className="between">
        <span className="badge">Đầu bếp đã xác minh</span>
        {chef.rating != null && chef.rating > 0 && <span className="small amber">★ {Number(chef.rating).toFixed(1)}</span>}
      </div>
      <h3 className="chef-card-title">{chef.displayName}</h3>
      <p className="small muted chef-card-area" title={provinceList(chef.serviceArea) || undefined}>{provinceList(chef.serviceArea) || 'Chưa cập nhật khu vực'}</p>
      {chef.bio && <p className="small chef-card-bio">{chef.bio}</p>}
      <p className="small muted chef-card-meta">{chef.menuCount} thực đơn công khai {chef.specialties ? `· ${chef.specialties}` : ''}</p>
      <div className="chef-card-footer">
        <span className="price">{chef.fromPrice != null ? <>{money(Number(chef.fromPrice))}<small> / người từ</small></> : 'Liên hệ để biết giá'}</span>
        <Link className="btn sm" to={`${ROUTES.CHEF_DETAIL}?id=${chef.id}`}>Xem gian bếp</Link>
      </div>
    </div>
  </article>;
}
