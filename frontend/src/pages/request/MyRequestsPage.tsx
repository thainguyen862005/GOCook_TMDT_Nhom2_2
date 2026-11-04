import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { workflowClient, type WorkflowRequest } from '../../clients/workflowClient';
import { AccountSidebar } from '../../components/account/AccountSidebar';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { money, requestDate } from '../../util/format';
import '../../styles/account-workflow.css';

function requestStatus(row: WorkflowRequest): string {
  if (row.status === 'CLOSED') {
    if (row.acceptedOrderStatus === 'CANCELLED') return 'Đơn đã hủy / hết hạn';
    if (row.acceptedEscrowStatus === 'RELEASED') return 'Đã hoàn thành';
    if (row.acceptedEscrowStatus === 'HELD') return 'Đã thanh toán';
    return 'Chờ thanh toán';
  }
  return row.status === 'REVIEWING' ? 'Đang trao đổi' : 'Đang tìm đầu bếp';
}

export function MyRequestsPage() {
  useDocumentTitle('Yêu cầu của tôi | GO Cook');
  const [rows, setRows] = useState<WorkflowRequest[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    workflowClient.mine()
      .then(items => { if (active) setRows(items); })
      .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : 'Không tải được yêu cầu.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [reload]);

  return <div className="container account-workflow-page">
    <div className="page-heading">
      <nav className="breadcrumb" aria-label="Đường dẫn">
        <Link to={ROUTES.HOME}>Trang chủ</Link><span>/</span><span>Yêu cầu nấu ăn</span>
      </nav>
      <h1>Yêu cầu nấu ăn của tôi</h1>
      <p className="lead">Theo dõi yêu cầu, đầu bếp được ghép và báo giá trong một nơi.</p>
    </div>

    <div className="workspace account-workspace">
      <AccountSidebar active="chat" />
      <section className="account-content request-overview" aria-label="Danh sách yêu cầu">
        <div className="request-toolbar">
          <p className="muted small" role="status">{loading ? 'Đang tải yêu cầu…' : `${rows.length} yêu cầu của bạn`}</p>
          <div className="row">
            <Link className="btn sm" to={ROUTES.REQUEST}>+ Tạo yêu cầu mới</Link>
            <button className="btn secondary sm" type="button" disabled={loading} onClick={() => setReload(n => n + 1)}>Làm mới</button>
          </div>
        </div>

        {error ? <div className="notice warm" role="alert">{error}</div>
          : loading ? <div className="card" role="status">Đang tải danh sách yêu cầu…</div>
            : rows.length === 0 ? <section className="card stack-sm">
              <h2>Bạn chưa có yêu cầu nào</h2>
              <p className="muted">Hãy tạo yêu cầu nấu ăn đầu tiên để tìm đầu bếp phù hợp.</p>
              <Link className="btn sm" to={ROUTES.REQUEST}>Tạo yêu cầu nấu ăn</Link>
            </section> : <div className="request-list">
              {rows.map(row => <article className="card request-card" key={row.id}>
                <div className="request-card-heading">
                  <div className="request-card-heading-main">
                    <p className="request-code">{row.requestCode}</p>
                    <h2 title={row.desiredDishes}>{row.desiredDishes || 'Yêu cầu nấu ăn'}</h2>
                  </div>
                  <span className={`badge ${row.status === 'CLOSED' ? 'gray' : ''}`}>{requestStatus(row)}</span>
                </div>
                <div className="request-facts">
                  <div><span>Ngày dùng bữa</span><strong>{requestDate(row.mealDate)}{row.mealTime ? ` · ${row.mealTime}` : ''}</strong></div>
                  <div><span>Khẩu phần</span><strong>{Number(row.adults || 0) + Number(row.children || 0)} người</strong></div>
                  <div><span>Ngân sách tối đa</span><strong>{money(row.budgetMax)}</strong></div>
                  <div><span>Báo giá</span><strong>{row.offerCount ?? 0} báo giá</strong></div>
                </div>
                <div className="request-card-footer">
                  <p className="small muted">Xem đầu bếp phù hợp, báo giá và lịch sử trao đổi.</p>
                  <Link className="btn secondary sm" to={`${ROUTES.REQUEST_DETAIL}?id=${row.id}`}>Xem chi tiết yêu cầu</Link>
                </div>
              </article>)}
            </div>}
      </section>
    </div>
  </div>;
}
