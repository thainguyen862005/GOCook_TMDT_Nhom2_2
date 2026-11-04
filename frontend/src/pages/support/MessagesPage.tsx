import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { workflowClient, type WorkflowThread } from '../../clients/workflowClient';
import { useAuth } from '../../hooks/auth/useAuth';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { conversationPath, sortAndFilterThreads, type ThreadFilter } from '../../util/messageThreads';
import '../../styles/messages.css';

function ConversationIcon({ kind }: { kind: WorkflowThread['kind'] }) {
  return kind === 'ORDER' ? (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="4" y="4" width="16" height="17" rx="2" />
      <path d="M8 2v4M16 2v4M4 10h16M8 14h8M8 17h5" />
    </svg>
  ) : (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 15a3 3 0 0 1-3 3H9l-6 3V6a3 3 0 0 1 3-3h11a3 3 0 0 1 3 3z" />
      <path d="M7 8h9M7 12h6" />
    </svg>
  );
}

function lastActivity(value: string | null): string {
  if (!value) return 'Chưa có tin nhắn';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return 'Chưa có tin nhắn';
  return `Hoạt động ${date.toLocaleTimeString('vi-VN', {hour: '2-digit', minute: '2-digit'})} · ${date.toLocaleDateString('vi-VN', {day: '2-digit', month: '2-digit', year: 'numeric'})}`;
}

/** The backend owns both participants and thread identity; filtering never changes authorization. */
export function MessagesPage() {
  useDocumentTitle('Hộp thư | GO Cook');
  const { profile } = useAuth();
  const [threads, setThreads] = useState<WorkflowThread[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<ThreadFilter>('all');

  useEffect(() => {
    if (!profile?.id) { setThreads([]); setLoading(false); return; }
    let active = true;
    setThreads([]); setLoading(true); setError('');
    workflowClient.threads().then(rows => { if (active) setThreads(rows); })
      .catch(err => { if (active) setError(err instanceof Error ? err.message : 'Không tải được hộp thư.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [profile?.id, refresh]);

  const visible = useMemo(() => sortAndFilterThreads(threads, query, filter), [threads, query, filter]);
  const requests = threads.filter(thread => thread.kind === 'REQUEST').length;
  const orders = threads.filter(thread => thread.kind === 'ORDER').length;

  return <div className="container messages-page">
    <div className="messages-heading">
      <div>
        <p className="eyebrow">TRUNG TÂM TRAO ĐỔI</p>
        <h1>Hộp thư</h1>
        <p className="muted">Các cuộc trò chuyện về yêu cầu nấu ăn và đơn đặt nấu.</p>
      </div>
      <button className="messages-refresh" type="button" onClick={() => setRefresh(value => value + 1)} disabled={loading}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20 11a8 8 0 1 1-2.3-5.7M20 4v5h-5" /></svg>
        Làm mới
      </button>
    </div>

    <section className="messages-panel" aria-label="Danh sách hội thoại">
      <div className="messages-controls">
        <div className="messages-control-title">
          <h2>Cuộc trò chuyện</h2>
          <span className="messages-count">{threads.length}</span>
        </div>
        <div className="messages-filters" role="group" aria-label="Lọc loại hội thoại">
          <button type="button" aria-pressed={filter === 'all'} className={filter === 'all' ? 'is-active' : ''} onClick={() => setFilter('all')}>Tất cả <span>{threads.length}</span></button>
          <button type="button" aria-pressed={filter === 'REQUEST'} className={filter === 'REQUEST' ? 'is-active' : ''} onClick={() => setFilter('REQUEST')}>Yêu cầu <span>{requests}</span></button>
          <button type="button" aria-pressed={filter === 'ORDER'} className={filter === 'ORDER' ? 'is-active' : ''} onClick={() => setFilter('ORDER')}>Đơn hàng <span>{orders}</span></button>
        </div>
        <label className="messages-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m16 16 5 5"/></svg>
          <span className="sr-only">Tìm hội thoại theo tên người hoặc mã yêu cầu</span>
          <input type="search" placeholder="Tìm tên hoặc mã…" value={query} onChange={event => setQuery(event.target.value)} />
        </label>
      </div>

      {error && <div className="messages-feedback messages-feedback--error" role="alert">
        <strong>Không thể tải hộp thư</strong><p>{error}</p>
        <button className="btn secondary sm" type="button" onClick={() => setRefresh(value => value + 1)}>Thử lại</button>
      </div>}
      {loading ? <p role="status" className="messages-feedback">Đang tải hội thoại…</p>
        : !error && !threads.length ? <div className="messages-feedback messages-empty">
          <span className="messages-empty-icon"><ConversationIcon kind="REQUEST" /></span>
          <h3>Chưa có cuộc trò chuyện</h3>
          <p>Hộp thư sẽ có nội dung khi bạn có yêu cầu được ghép hoặc đơn đã được tạo.</p>
          <Link className="btn sm" to={ROUTES.MY_REQUESTS}>Xem yêu cầu của tôi</Link>
        </div> : !error && !visible.length ? <div className="messages-feedback messages-empty">
          <h3>Không tìm thấy hội thoại</h3>
          <p>Hãy thử từ khóa khác hoặc chọn “Tất cả”.</p>
          <button type="button" className="btn secondary sm" onClick={() => { setQuery(''); setFilter('all'); }}>Xóa bộ lọc</button>
        </div> : !error ? <div className="messages-list">
          {visible.map(thread => {
            const order = thread.kind === 'ORDER';
            const peer = thread.peerName || `Tài khoản #${thread.peerId}`;
            return <Link className="messages-thread" key={`${thread.kind}:${thread.scopeId}:${thread.peerId}`}
              to={conversationPath(thread, ROUTES.CHAT)} aria-label={`Mở hội thoại với ${peer} về ${order ? 'đơn hàng' : 'yêu cầu'} ${thread.title}`}>
              <span className={`messages-thread-icon ${order ? 'is-order' : 'is-request'}`}><ConversationIcon kind={thread.kind} /></span>
              <span className="messages-thread-main">
                <span className="messages-thread-top"><strong className="messages-thread-name">{peer}</strong><span className="messages-thread-time">{lastActivity(thread.lastMessageAt)}</span></span>
                <span className="messages-thread-meta"><span className={`messages-kind ${order ? 'is-order' : 'is-request'}`}>{order ? 'Đơn hàng' : 'Yêu cầu'}</span><span className="messages-thread-code" title={thread.title}>{thread.title}</span></span>
              </span>
              <span className="messages-thread-arrow" aria-hidden="true">→</span>
            </Link>;
          })}
        </div> : null}
    </section>
  </div>;
}
