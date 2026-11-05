import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { chatbotClient, type ChatContext, type ChatReply } from '../../clients/chatbotClient';
import { useAuth } from '../../hooks/auth/useAuth';
import { ROUTES } from '../../constants/routes';
import '../../styles/chatbot.css';

type ChatLine = { id: number; speaker: 'user' | 'bot'; text: string; result?: ChatReply };
const SUGGESTIONS = [
  'Tìm món chay cho 4 người dưới 800k',
  'Tìm đầu bếp ở Gia Lai',
  'Gợi ý món phù hợp với tôi',
];

export function ChatbotWidget() {
  const { profile, isSeller, isAdmin } = useAuth();
  const personalized = Boolean(profile && !isSeller && !isAdmin);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [context, setContext] = useState<ChatContext | null>(null);
  const [lines, setLines] = useState<ChatLine[]>([
    { id: 1, speaker: 'bot', text: 'Xin chào! Mình là GO Cook Assistant. Mình có thể tìm thực đơn, Chef và hướng dẫn các chức năng của website.' },
  ]);
  const scrollBox = useRef<HTMLDivElement>(null);
  const id = useRef(2);
  useEffect(() => { if (open) scrollBox.current?.scrollTo({ top: scrollBox.current.scrollHeight, behavior: 'smooth' }); }, [lines, open]);
  // Do not carry customer-specific chat contexts across account switches.
  useEffect(() => { setContext(null); setLines([{ id: 1, speaker: 'bot', text: 'Mình có thể tìm thực đơn, đầu bếp hoặc hướng dẫn sử dụng GO Cook. Bạn cần hỗ trợ gì?' }]); }, [profile?.id]);

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = draft.trim();
    if (!text || busy || text.length > 500) return;
    setDraft('');
    setBusy(true);
    setLines((old) => [...old, { id: id.current++, speaker: 'user', text }]);
    try {
      const answer = await chatbotClient.send(text, context, personalized);
      setContext(answer.context);
      setLines((old) => [...old, { id: id.current++, speaker: 'bot', text: answer.message, result: answer }]);
    } catch (error) {
      setLines((old) => [...old, {
        id: id.current++, speaker: 'bot',
        text: `Không thể kết nối chatbot (${error instanceof Error ? error.message : 'lỗi không xác định'}). Bạn có thể liên hệ tại trang Hỗ trợ.`,
      }]);
    } finally { setBusy(false); }
  }

  return (
    <div className="gocook-chatbot-root">
      {open && (
        <section className="gocook-chatbot-panel" role="dialog" aria-label="GO Cook Assistant">
          <header className="gocook-chatbot-header">
            <div><strong>GO Cook AI</strong><span>Tư vấn thực đơn và đầu bếp</span></div>
            <button type="button" aria-label="Đóng chatbot" onClick={() => setOpen(false)}>×</button>
          </header>
          <div className="gocook-chatbot-feed" ref={scrollBox} aria-live="polite" aria-relevant="additions">
            {lines.map((line) => (
              <div className={`gocook-chatbot-line ${line.speaker}`} key={line.id}>
                <p>{line.text}</p>
                {line.result?.menus?.map((menu) => (
                  <article className="gocook-chatbot-card" key={`menu-${menu.id}`}>
                    <b>{menu.name}</b>
                    <span>{menu.category} · {Number(menu.unitPrice).toLocaleString('vi-VN')}đ/người</span>
                    <small>{menu.reason}</small>
                    <Link to={`${ROUTES.MENU_DETAIL}?id=${menu.id}`} onClick={() => setOpen(false)}>Xem thực đơn</Link>
                  </article>
                ))}
                {line.result?.chefs?.map((chef) => (
                  <article className="gocook-chatbot-card" key={`chef-${chef.id}`}>
                    <b>{chef.displayName}</b>
                    <span>Đánh giá {chef.rating.toFixed(1)}/5 · {chef.specialty || 'Đầu bếp đã xác minh'}</span>
                    {chef.serviceArea && <small>Khu vực phục vụ: {chef.serviceArea}</small>}
                    <Link to={`${ROUTES.CHEF_DETAIL}?id=${chef.id}`} onClick={() => setOpen(false)}>Xem Chef</Link>
                  </article>
                ))}
                {line.result?.helpUrl && (
                  <Link className="gocook-chatbot-help" to={ROUTES.HELP} onClick={() => setOpen(false)}>Liên hệ Admin / Trung tâm trợ giúp</Link>
                )}
                {line.speaker === 'bot' && line.text.startsWith('Không thể kết nối chatbot') && <Link to={ROUTES.HELP}>Liên hệ Admin</Link>}
                {line.result?.intent === 'CART_HELP' && <Link to={ROUTES.CART} onClick={() => setOpen(false)}>Mở giỏ hàng</Link>}
                {line.result?.intent === 'ORDER_HELP' && <Link to={profile ? ROUTES.ORDERS : ROUTES.LOGIN} onClick={() => setOpen(false)}>Xem đơn hàng</Link>}
              </div>
            ))}
            {busy && <p className="gocook-chatbot-busy">Đang tìm thông tin trên GO Cook...</p>}
          </div>
          {lines.length <= 1 && <div className="gocook-chatbot-prompts">{SUGGESTIONS.map(s => (
            <button type="button" key={s} onClick={() => setDraft(s)}>{s}</button>
          ))}</div>}
          <form className="gocook-chatbot-form" onSubmit={send}>
            <input aria-label="Nhập câu hỏi cho GO Cook Assistant" value={draft} onChange={e => setDraft(e.target.value)}
              placeholder="Nhập câu hỏi..." maxLength={500} disabled={busy}/>
            <button type="submit" disabled={busy || !draft.trim()}>Gửi</button>
          </form>
          <p className="gocook-chatbot-disclaimer">Gợi ý từ dữ liệu GO Cook · Lịch phục vụ cần xác nhận với Chef</p>
        </section>
      )}
      <button className="gocook-chatbot-launcher" type="button" aria-expanded={open}
        aria-label={open ? 'Đóng trợ lý GO Cook AI' : 'Hỏi trợ lý GO Cook AI'}
        title={open ? 'Đóng trợ lý AI' : 'Hỏi GO Cook AI'}
        onClick={() => setOpen(v => !v)}>
        {open ? <span className="gocook-chatbot-close" aria-hidden="true">×</span> : (
          <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20 15a3 3 0 0 1-3 3H9l-6 3V6a3 3 0 0 1 3-3h11a3 3 0 0 1 3 3z" />
            <path d="M7 8h9M7 12h6" />
          </svg>
        )}
      </button>
    </div>
  );
}
