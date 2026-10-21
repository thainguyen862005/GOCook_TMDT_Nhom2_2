import { Link } from 'react-router-dom';
import type { ChefSummary } from '../../types/publicContent';
import { ROUTES } from '../../constants/routes';
import { money } from '../../util/format';

function ChefPortrait({ url, name }: { url: string | null; name: string }) {
  return <img src={url || '/assets/chef.png'} loading="lazy" alt={`Đầu bếp ${name}`}
    onError={event => { if (!event.currentTarget.src.endsWith('/assets/chef.png')) event.currentTarget.src = '/assets/chef.png'; }} />;
}

export function ChefCard({ chef }: { chef: ChefSummary }) {
  return <article className="card chef-card">
    <ChefPortrait url={chef.avatarUrl} name={chef.displayName} />
    <div className="card-body stack-sm">
      <div className="between">
        <span className="badge">Đầu bếp đã xác minh</span>
        {chef.rating != null && chef.rating > 0 && <span className="small amber">★ {Number(chef.rating).toFixed(1)}</span>}
      </div>
      <h3>{chef.displayName}</h3>
      <p className="small muted">{chef.serviceArea || 'Chưa cập nhật khu vực'}</p>
      {chef.bio && <p className="small">{chef.bio}</p>}
      <p className="small muted">{chef.menuCount} thực đơn công khai {chef.specialties ? `· ${chef.specialties}` : ''}</p>
      <div className="between">
        <span className="price">{chef.fromPrice != null ? <>{money(Number(chef.fromPrice))}<small> / người từ</small></> : 'Liên hệ để biết giá'}</span>
        <Link className="btn sm" to={`${ROUTES.CHEF_DETAIL}?id=${chef.id}`}>Xem gian bếp</Link>
      </div>
    </div>
  </article>;
}
