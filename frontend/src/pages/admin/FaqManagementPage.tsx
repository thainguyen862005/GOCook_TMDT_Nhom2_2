import { useEffect, useState, type FormEvent } from 'react';
import { publicContentClient } from '../../clients/publicContentClient';
import { AdminLayout } from '../../layouts/AdminLayout';
import type { EditableFaq } from '../../types/publicContent';

const empty = { question: '', answer: '', category: 'Chung', displayOrder: 0, active: true };
export function FaqManagementPage() {
  const [items, setItems] = useState<EditableFaq[]>([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    publicContentClient.adminFaqs().then(setItems).catch(reason => setError(reason instanceof Error ? reason.message : 'Không tải được FAQ.'));
  }, [retry]);
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return; const formElement = event.currentTarget; setBusy(true); setError(''); setNotice('');
    const form = new FormData(event.currentTarget);
    try {
      await publicContentClient.createFaq({ question: String(form.get('question') || ''),
        answer: String(form.get('answer') || ''), category: String(form.get('category') || 'Chung'),
        displayOrder: Number(form.get('displayOrder') || 0), active: true });
      setNotice('Đã tạo câu hỏi.'); formElement.reset(); setRetry(x => x + 1);
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Không thể tạo FAQ.'); }
    finally { setBusy(false); }
  }
  async function save(item: EditableFaq) {
    setBusy(true); setError(''); setNotice('');
    try { await publicContentClient.updateFaq(item.id, { question: item.question, answer: item.answer,
      category: item.category, displayOrder: item.displayOrder, active: item.active }); setNotice(`Đã lưu câu hỏi #${item.id}.`); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Không thể lưu FAQ.'); }
    finally { setBusy(false); }
  }
  function edit(id: number, key: keyof EditableFaq, value: string | number | boolean) {
    setItems(prev => prev.map(row => row.id === id ? { ...row, [key]: value } : row));
  }
  return <AdminLayout title="Quản lý câu hỏi thường gặp" lead="Nội dung FAQ hiển thị công khai trên trang Trợ giúp; có thể chỉnh sửa và bật/tắt mà không cần sửa code.">
    {error && <p role="alert" className="notice warm">{error}</p>}
    {notice && <p role="status" className="notice">{notice}</p>}
    <form className="card stack" onSubmit={create}><h2>Thêm câu hỏi</h2>
      <label className="field">Câu hỏi<input required maxLength={255} name="question" /></label>
      <label className="field">Trả lời<textarea required maxLength={4000} name="answer" rows={4} /></label>
      <div className="grid grid-2"><label className="field">Nhóm<input name="category" defaultValue={empty.category} maxLength={80} /></label>
        <label className="field">Thứ tự<input type="number" min={0} max={100000} name="displayOrder" defaultValue={0} /></label></div>
      <button type="submit" className="btn" disabled={busy}>Thêm câu hỏi</button>
    </form>
    {items.map(item => <section key={item.id} className="card stack">
      <label className="field">Câu hỏi<input value={item.question} maxLength={255} onChange={event => edit(item.id,'question',event.target.value)} /></label>
      <label className="field">Trả lời<textarea value={item.answer} rows={4} maxLength={4000} onChange={event => edit(item.id,'answer',event.target.value)} /></label>
      <div className="grid grid-2"><label className="field">Nhóm<input value={item.category} onChange={event => edit(item.id,'category',event.target.value)} /></label>
        <label className="field">Thứ tự<input type="number" min={0} max={100000} value={item.displayOrder} onChange={event => edit(item.id,'displayOrder',Number(event.target.value))} /></label></div>
      <label className="check"><input type="checkbox" checked={item.active} onChange={event => edit(item.id,'active',event.target.checked)} />Công khai câu hỏi</label>
      <button className="btn secondary" disabled={busy} type="button" onClick={() => save(item)}>Lưu câu hỏi #{item.id}</button>
    </section>)}
    <button className="btn secondary" onClick={() => setRetry(x => x + 1)}>Tải lại danh sách</button>
  </AdminLayout>;
}
