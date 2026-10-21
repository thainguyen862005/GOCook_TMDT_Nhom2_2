import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { publicContentClient } from '../../clients/publicContentClient';
import { ROUTES } from '../../constants/routes';
import { useAuth } from '../../hooks/auth/useAuth';
import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import type { Faq } from '../../types/publicContent';

export function HelpPage() {
  useDocumentTitle('Trung tâm trợ giúp | GO Cook');
  const { profile } = useAuth();
  const [faqs, setFaqs] = useState<Faq[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [sending, setSending] = useState(false);
  const [ticket, setTicket] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setError('');
    publicContentClient.faqs(abort.signal).then(result => { if (!abort.signal.aborted) setFaqs(result); })
      .catch(err => { if (!abort.signal.aborted) setError(err instanceof Error ? err.message : 'Không tải được FAQ.'); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [retry]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (sending) return;
    const formElement = event.currentTarget;
    setSending(true); setError(''); setTicket('');
    const form = new FormData(event.currentTarget);
    try {
      const saved = await publicContentClient.ticket({
        subject: String(form.get('subject') || '').trim(), message: String(form.get('message') || '').trim(),
      });
      setTicket(saved.ticketCode); formElement.reset();
    } catch (err) { setError(err instanceof Error ? err.message : 'Gửi yêu cầu thất bại.'); }
    finally { setSending(false); }
  }
  return <div className="container">
    <div className="page-heading"><nav className="breadcrumb"><Link to="/">Trang chủ</Link><span>/</span><span>Trợ giúp</span></nav>
      <h1>Bạn cần hỗ trợ điều gì?</h1><p className="lead">Các câu hỏi được cập nhật từ hệ thống quản trị. Bạn cũng có thể gửi yêu cầu đến bộ phận hỗ trợ.</p></div>
    <div className="split">
      <section className="card stack"><h2>Câu hỏi thường gặp</h2>
        {loading ? <p role="status">Đang tải câu hỏi…</p> : faqs.length === 0 ? <p>Chưa có câu hỏi công khai.</p>
          : faqs.map(item => <details className="faq" key={item.id}><summary>{item.question}</summary><p>{item.answer}</p></details>)}
        {error && <button className="btn secondary sm" onClick={() => setRetry(x => x + 1)}>Tải lại FAQ</button>}
      </section>
      <section className="card stack"><h2>Gửi yêu cầu hỗ trợ</h2>
        {!profile ? <p>Đăng nhập để gửi yêu cầu hỗ trợ an toàn và nhận mã theo dõi. <Link to={ROUTES.LOGIN}>Đăng nhập</Link></p>
          : <form className="stack" onSubmit={submit}>
            <p className="small muted">Ticket được gửi bằng tài khoản đã xác minh, không thể thay email người gửi.</p>
            <label className="field">Chủ đề<select name="subject" required>
              <option>Đặt lịch nấu ăn</option><option>Thanh toán</option><option>Thay đổi hoặc hủy lịch</option><option>Vấn đề khác</option>
            </select></label>
            <label className="field">Nội dung<textarea name="message" required minLength={10} maxLength={5000} rows={6} placeholder="Mô tả vấn đề bạn đang gặp..." /></label>
            <button className="btn" disabled={sending} type="submit">{sending ? 'Đang gửi…' : 'Gửi yêu cầu'}</button>
          </form>}
        {ticket && <p role="status" className="notice">Đã ghi nhận yêu cầu <strong>{ticket}</strong>. Bộ phận hỗ trợ sẽ xem trong Admin Tickets.</p>}
        {error && <p role="alert" className="notice warm">{error}</p>}
      </section>
    </div>
  </div>;
}
