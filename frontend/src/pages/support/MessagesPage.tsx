import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { workflowClient, type WorkflowThread } from '../../clients/workflowClient';
import { useAuth } from '../../hooks/auth/useAuth';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';

/** The backend owns both participants and thread identity. The UI does not infer recipients. */
export function MessagesPage() {
  useDocumentTitle('Hộp thư | GO Cook');
  const { profile } = useAuth();
  const [threads, setThreads] = useState<WorkflowThread[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    if (!profile?.id) { setThreads([]); setLoading(false); return; }
    let active = true;
    setThreads([]); setLoading(true); setError('');
    workflowClient.threads().then(rows => { if (active) setThreads(rows); })
      .catch(err => { if(active) setError(err instanceof Error ? err.message : 'Không tải được hộp thư.'); })
      .finally(() => { if(active) setLoading(false); });
    return () => { active = false; };
  }, [profile?.id, refresh]);

  return <div className="container stack messages-page">
    <div className="page-heading"><h1>Hộp thư</h1><p className="lead">Trao đổi trực tiếp về từng yêu cầu hoặc đơn. Khách và Chef nhìn thấy cùng một hội thoại.</p></div>
    <div className="row"><Link className="btn secondary" to={ROUTES.ACCOUNT}>Tài khoản</Link>
      <button type="button" className="btn secondary" onClick={() => setRefresh(value=>value+1)}>Làm mới</button></div>
    {error && <section className="notice warm" role="alert">{error}<p className="small">Nếu backend báo thiếu bảng tin nhắn, hãy chạy các migration workflow trước khi kiểm thử.</p></section>}
    {loading ? <p role="status">Đang tải hộp thư…</p> : threads.length===0 && !error ? <section className="card stack"><h2>Chưa có hội thoại</h2><p>Hội thoại sẽ xuất hiện khi Chef được ghép vào yêu cầu hoặc được gán đơn.</p><Link to={ROUTES.MY_REQUESTS}>Xem yêu cầu</Link></section> :
      <div className="grid grid-2">{threads.map(thread => {
        const query = thread.kind==='ORDER' ? `orderId=${thread.scopeId}` : `requestId=${thread.scopeId}&peerId=${thread.peerId}`;
        return <article className="card stack" key={`${thread.kind}:${thread.scopeId}:${thread.peerId}`}>
          <div className="between"><strong>{thread.kind==='ORDER' ? 'Đơn hàng' : 'Yêu cầu'}</strong><span className="badge gray">{thread.title}</span></div>
          <h3>{thread.peerName || `Tài khoản #${thread.peerId}`}</h3>
          <p className="small muted">{thread.lastMessageAt ? `Tin nhắn gần nhất: ${new Date(thread.lastMessageAt).toLocaleString('vi-VN')}` : 'Chưa có tin nhắn, bạn có thể bắt đầu.'}</p>
          <Link className="btn secondary" to={`${ROUTES.CHAT}?${query}`}>Mở hội thoại</Link>
        </article>;
      })}</div>}
  </div>;
}
