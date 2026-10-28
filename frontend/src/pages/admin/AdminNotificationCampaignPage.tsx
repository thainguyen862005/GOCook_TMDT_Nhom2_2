import { useState, type FormEvent } from 'react';
import { AdminLayout } from '../../layouts/AdminLayout';
import { notificationClient } from '../../clients/notificationClient';

export function AdminNotificationCampaignPage() {
  const [target,setTarget] = useState('ALL');
  const [category,setCategory] = useState('SYSTEM');
  const [title,setTitle] = useState('');
  const [body,setBody] = useState('');
  const [sending,setSending] = useState(false);
  const [result,setResult] = useState('');
  const [error,setError] = useState('');
  const send=async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!window.confirm('Gửi thông báo này đến nhóm người nhận đã chọn? Không thể thu hồi sau khi gửi.')) return;
    setSending(true);setResult('');setError('');
    try { const response=await notificationClient.sendCampaign(target,category,title.trim(),body.trim());
      setResult(`Đã gửi cho ${response.recipients} tài khoản.`);setTitle('');setBody('');
    } catch { setError('Gửi thông báo thất bại. Kiểm tra phân quyền, số lượt gửi và kết nối backend.'); }
    finally { setSending(false); }
  };
  return <AdminLayout title="Gửi thông báo" lead="Gửi thông báo nội bộ cho khách hàng, đầu bếp hoặc tài khoản quản trị.">
    <form className="card admin-notice-form" onSubmit={e => void send(e)}>
      <label>Nhóm người nhận<select value={target} onChange={e => setTarget(e.target.value)}>
        <option value="ALL">Tất cả tài khoản đang hoạt động</option>
        <option value="CUSTOMER">Khách hàng</option>
        <option value="CHEF">Đầu bếp</option>
        <option value="ADMIN">Quản trị viên</option>
        <option value="COMPANY_ADMIN">Quản trị tổ chức</option>
      </select></label>
      <label>Loại thông báo<select value={category} onChange={e=>setCategory(e.target.value)}>
        <option value="SYSTEM">Thông báo hệ thống (bắt buộc)</option>
        <option value="PROMOTION">Ưu đãi / marketing (tôn trọng tùy chọn người nhận)</option>
      </select></label>
      <label>Tiêu đề<input required maxLength={180} value={title} onChange={e=>setTitle(e.target.value)} /></label>
      <label>Nội dung<textarea required maxLength={600} rows={5} value={body} onChange={e=>setBody(e.target.value)} /></label>
      <p>Tối đa 10 lượt gửi mỗi 24 giờ cho mỗi Admin. Thao tác được lưu lịch sử trong database.</p>
      {error && <p role="alert" className="notice-error">{error}</p>}
      {result && <p role="status">{result}</p>}
      <button type="submit" className="btn" disabled={sending || !title.trim() || !body.trim()}>
        {sending ? 'Đang gửi...' : 'Gửi thông báo'}
      </button>
    </form>
  </AdminLayout>;
}
