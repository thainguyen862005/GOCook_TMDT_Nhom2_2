import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { notificationClient, internalNotificationPath } from '../../clients/notificationClient';
import { useAuth } from '../../hooks/auth/useAuth';
import type { AppNotification } from '../../types/notification';

/** Shared inbox: signed-in customer, chef and system admin. No client-supplied user IDs. */
export function NotificationBell() {
  const { profile } = useAuth();
  const [open, setOpen] = useState(false);
  const [count, setCount] = useState(0);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const holder = useRef<HTMLDivElement>(null);
  const currentUserId = useRef<number | null>(profile?.id ?? null);
  currentUserId.current = profile?.id ?? null;
  const location = useLocation();

  const reload = useCallback(async (showErrors = false) => {
    if (!profile) return;
    const requestFor = profile.id;
    try {
      const [n, page] = await Promise.all([
        notificationClient.unreadCount(), notificationClient.page(0, 6),
      ]);
      if (currentUserId.current !== requestFor) return;
      setCount(n.unread);
      setItems(page.content);
      setError('');
    } catch {
      if (showErrors && currentUserId.current === requestFor) setError('Không tải được thông báo. Hãy thử lại.');
    }
  }, [profile?.id]);

  useEffect(() => {
    // An account switch must never temporarily show the previous user's inbox.
    setOpen(false);
    setCount(0);
    setItems([]);
    setError('');
    if (!profile) return;
    let active = true;
    void reload();
    const timer = window.setInterval(() => {
      if (active && document.visibilityState === 'visible') void reload();
    }, 30000);
    const onFocus = () => { if (active) void reload(); };
    window.addEventListener('focus', onFocus);
    return () => { active = false; window.clearInterval(timer); window.removeEventListener('focus', onFocus); };
  }, [profile?.id, reload]);

  useEffect(() => { setOpen(false); }, [location.pathname]);
  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (holder.current && !holder.current.contains(event.target as Node)) setOpen(false);
    };
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', escape); };
  }, []);

  if (!profile) return null;

  const mark = async (id: number) => {
    try { await notificationClient.read(id); await reload(); }
    catch { setError('Không đánh dấu đã đọc được.'); }
  };

  return (
    <div ref={holder} className="notice-bell-wrapper">
      <button type="button" className="notice-bell-button" aria-label={`Thông báo, ${count} chưa đọc`}
        aria-expanded={open} aria-haspopup="dialog"
        onClick={() => { setOpen(v => !v); if (!open) void reload(true); }}>
        <svg viewBox="0 0 24 24" width="21" height="21" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
        </svg>
        {count > 0 && <span className="notice-unread-badge">{count > 99 ? '99+' : count}</span>}
      </button>
      {open && <section className="notice-popover" role="dialog" aria-label="Thông báo mới">
        <div className="notice-popover-header"><strong>Thông báo</strong>
          <button type="button" onClick={() => setOpen(false)} aria-label="Đóng">×</button></div>
        {error && <p role="alert" className="notice-error">{error} <button type="button" onClick={() => void reload(true)}>Thử lại</button></p>}
        <div className="notice-list">
          {items.length === 0 && <p className="notice-empty">Chưa có thông báo nào.</p>}
          {items.map(item => <div key={item.id} className={`notice-item ${item.read ? '' : 'is-unread'}`}>
            <Link to={internalNotificationPath(item.actionPath)} onClick={() => { if (!item.read) void mark(item.id); setOpen(false); }}>
              <strong>{item.title}</strong><span>{item.body}</span>
              <small>{new Date(item.createdAt).toLocaleString('vi-VN')}</small>
            </Link>
            {!item.read && <button type="button" aria-label={`Đánh dấu đã đọc: ${item.title}`} onClick={() => void mark(item.id)}>Đã đọc</button>}
          </div>)}
        </div>
        <div className="notice-popover-footer">
          <Link to="/notifications" onClick={() => setOpen(false)}>Xem tất cả</Link>
          <button type="button" disabled={busy || count === 0} onClick={async () => {
            setBusy(true); try { await notificationClient.readAll(); await reload(); }
            catch { setError('Không cập nhật được thông báo.'); } finally { setBusy(false); }
          }}>Đánh dấu tất cả đã đọc</button>
        </div>
      </section>}
    </div>
  );
}
