import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
export function ReviewPage() {
  useDocumentTitle('Đánh giá dịch vụ | GO Cook');
  const [params]=useSearchParams();
  const id=Number(params.get('orderId'));
  return <div className="container page-heading stack"><h1>Đánh giá dịch vụ</h1>
    <p>Đánh giá được gửi từ đơn đã hoàn thành, giúp xác minh người đánh giá đã sử dụng dịch vụ.</p>
    <Link className="btn" to={Number.isSafeInteger(id)&&id>0?`${ROUTES.ORDER_DETAIL}?id=${id}`:ROUTES.ORDERS}>
      {id>0?'Đến đơn hàng để đánh giá':'Xem đơn hàng của tôi'}
    </Link></div>;
}
