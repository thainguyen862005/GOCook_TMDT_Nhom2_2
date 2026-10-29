import { Link, useSearchParams } from 'react-router-dom';
import { RequestDetailPage } from './RequestDetailPage';
import { ROUTES } from '../../constants/routes';
/** Legacy /quotes now shows real request offers rather than a static demo. */
export function QuotesPage(){const [params]=useSearchParams();
  if(params.has('id'))return <RequestDetailPage/>;
  return <div className="container card stack"><h1>Báo giá của bạn</h1><p>Chọn một yêu cầu để xem báo giá được gửi từ đầu bếp thật.</p><Link className="btn" to={ROUTES.MY_REQUESTS}>Xem yêu cầu của tôi</Link></div>;
}
