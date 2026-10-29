import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../hooks/auth/useAuth';
import { workflowClient, type WorkflowMessage } from '../../clients/workflowClient';
import { ROUTES } from '../../constants/routes';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';

/** Request-scoped DB chat. Loading, conversation switches and user changes never expose stale messages. */
export function ChatPage() {
  useDocumentTitle('Tin nhắn | GO Cook');
  const { profile } = useAuth();
  const [params] = useSearchParams();
  const orderId = Number(params.get('orderId'));
  const isOrder = params.has('orderId');
  const requestId = Number(params.get('requestId'));
  const peerId = Number(params.get('peerId'));
  const valid = isOrder ? Number.isSafeInteger(orderId) && orderId > 0
    : Number.isSafeInteger(requestId) && requestId > 0
      && Number.isSafeInteger(peerId) && peerId > 0 && peerId !== profile?.id;
  const [messages, setMessages] = useState<WorkflowMessage[]>([]);
  const [body, setBody] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(valid);
  const [busy, setBusy] = useState(false);
  const conversation = `${isOrder ? 'o' : 'r'}:${isOrder ? orderId : requestId}:${peerId}:${profile?.id || 0}`;
  const conversationRef = useRef(conversation);
  conversationRef.current = conversation;
  const backTo = ROUTES.MESSAGES;

  useEffect(() => {
    let current = true;
    setMessages([]);
    setBody('');
    setBusy(false);
    setError('');
    setLoading(valid);
    if (!valid) return () => { current = false; };

    const refresh = async () => {
      try {
        const result = isOrder ? await workflowClient.orderMessages(orderId) : await workflowClient.messages(requestId, peerId);
        if (current) { setMessages(result.slice().reverse()); setError(''); }
      } catch (reason) {
        if (current) setError(reason instanceof Error ? reason.message : 'Không tải được hội thoại. Kiểm tra quyền tham gia và migration chat.');
      } finally {
        if (current) setLoading(false);
      }
    };
    void refresh();
    const interval = window.setInterval(() => {
      if (document.visibilityState === 'visible') void refresh();
    }, 8000);
    return () => { current = false; window.clearInterval(interval); };
  }, [isOrder, orderId, requestId, peerId, profile?.id, valid]);

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = body.trim();
    if (!valid || !text || busy) return;
    setBusy(true);
    setError('');
    const key = conversation;
    try {
      if (isOrder) await workflowClient.sendOrderMessage(orderId, text);
      else await workflowClient.send(requestId, peerId, text);
      if (conversationRef.current !== key) return;
      setBody('');
      const result = isOrder ? await workflowClient.orderMessages(orderId) : await workflowClient.messages(requestId, peerId);
      if (conversationRef.current === key) setMessages(result.slice().reverse());
    } catch (reason) {
      if (conversationRef.current === key) setError(reason instanceof Error ? reason.message : 'Không gửi được tin nhắn.');
    } finally { setBusy(false); }
  }

  return <div className="container stack">
    <div className="page-heading">
      <nav className="breadcrumb" aria-label="Đường dẫn">
        <Link to={backTo}>Hộp thư</Link><span>/</span><span>Hội thoại</span>
      </nav>
      <h1>{isOrder ? 'Trao đổi về đơn đặt nấu' : 'Trao đổi về yêu cầu nấu ăn'}</h1>
      <p className="lead">Tin nhắn lưu theo {isOrder ? 'đơn hàng' : 'yêu cầu'}. Chỉ khách và đầu bếp có quyền truy cập cuộc trò chuyện.</p>
      <Link className="text-link" to={backTo}>Quay lại hộp thư</Link>
    </div>
    {!valid ? <section className="card stack">
      <p>Chưa có hội thoại được chọn. Hãy mở tin nhắn từ hộp thư hoặc chi tiết đơn.</p>
      <Link className="btn" to={backTo}>Mở hộp thư</Link>
    </section> : <section className="card chat-shell">
      <div className="chat-stream" aria-live="polite" aria-label="Nội dung tin nhắn">
        {loading ? <p role="status" className="muted">Đang tải hội thoại…</p>
          : !messages.length && !error ? <p className="muted">Chưa có tin nhắn. Bạn có thể bắt đầu trao đổi ngay tại đây.</p> : null}
        {messages.map(message => <div className={`bubble ${message.senderId === profile?.id ? 'mine' : ''}`} key={message.id}>
          <strong>{message.senderName}</strong>
          <p>{message.body}</p>
          <p className="small muted">{new Date(message.createdAt).toLocaleString('vi-VN')}</p>
        </div>)}
      </div>
      {error && <p role="alert" className="notice warm">{error}</p>}
      <form className="chat-compose" onSubmit={event => void send(event)}>
        <label className="sr-only" htmlFor="chat-body">Nội dung tin nhắn</label>
        <input id="chat-body" className="input" maxLength={2000} value={body}
          onChange={event => setBody(event.target.value)} placeholder="Nhập tin nhắn…" required />
        <button className="btn sm" disabled={busy || loading || !valid || !body.trim()}>
          {busy ? 'Đang gửi…' : 'Gửi'}
        </button>
      </form>
    </section>}
  </div>;
}
