import { useEffect, useState, type FormEvent } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { adminOperationsClient, type AdminOpsModule, type AdminOpsPage, type AdminRow } from '../../clients/adminOperationsClient';
import { AdminLayout } from '../../layouts/AdminLayout';

interface ModuleConfig {
  title: string;
  desc: string;
  columns: Array<[string, string]>;
  filters?: string[];
  fields?: Array<[string, string, 'text' | 'number' | 'date' | 'select' | 'textarea', string?]>;
  options?: string[];
  warning?: string;
}

const CONFIG: Record<AdminOpsModule, ModuleConfig> = {
  customers: {
    title: 'Khách hàng', desc: 'Hồ sơ, số đơn đã đặt và lịch sử chi tiêu theo đơn hoàn thành.',
    columns: [['id','ID'],['username','Khách hàng'],['email','Email'],['is_active','Hoạt động'],['bookings','Số đơn'],['completed_order_value','Giá trị đơn đã hoàn thành']],
    filters: ['true','false'],
  },
  chefs: {
    title: 'Quản lý & xác minh Chef', desc: 'Duyệt Chef có lý do. Việc khóa Chef bị từ chối khi còn booking sắp tới.',
    columns: [['id','ID'],['display_name','Tên đầu bếp'],['email','Email'],['service_area','Khu vực'],['rating','Đánh giá'],['verification_status','Xác minh'],['is_active','Hoạt động']],
    filters: ['PENDING','APPROVED','REJECTED','MORE_INFO'],
  },
  payments: {
    title: 'Giao dịch thanh toán', desc: 'Chỉ đọc giao dịch gốc. Không sửa số tiền hoặc trạng thái VNPay trong Admin.',
    columns: [['id','ID'],['order_code','Mã đơn'],['customer_name','Khách hàng'],['provider','Cổng'],['amount','Số tiền'],['status','Trạng thái'],['paid_at','Đã thanh toán']],
    filters: ['PENDING','SUCCESS','FAILED','CANCELLED','REFUNDED'],
  },
  vouchers: {
    title: 'Mã ưu đãi', desc: 'Quản lý chính sách voucher. Chưa tự áp dụng ở checkout khi Payment chưa tích hợp.',
    columns: [['id','ID'],['code','Mã'],['discount_type','Loại'],['discount_value','Giá trị'],['used_count','Đã dùng'],['usage_limit','Giới hạn'],['is_active','Kích hoạt']],
    fields: [['code','Mã voucher','text'],['description','Mô tả','text'],['discountType','Kiểu giảm','select','PERCENT,FIXED'],
      ['discountValue','Giá trị giảm','number'],['minOrderAmount','Giá trị đơn tối thiểu','number'],['maxDiscount','Giảm tối đa (có thể bỏ trống)','number'],
      ['usageLimit','Giới hạn lượt dùng','number'],['startsAt','Bắt đầu','date'],['endsAt','Kết thúc','date']],
    warning: 'Voucher mới mặc định tắt; engine áp dụng mã trong checkout thuộc phạm vi Payment.',
  },
  cases: {
    title: 'Khiếu nại', desc: 'Tiếp nhận, ưu tiên, xử lý nội bộ và đóng case có ghi nhận lịch sử.',
    columns: [['id','ID'],['case_code','Mã'],['subject','Vấn đề'],['priority','Ưu tiên'],['status','Trạng thái'],['created_at','Tạo lúc']],
    filters: ['OPEN','IN_REVIEW','RESOLVED','CLOSED'],
    fields: [['subject','Tiêu đề','text'],['description','Mô tả','textarea'],['bookingId','Mã booking (tùy chọn)','number'],['priority','Ưu tiên','select','LOW,MEDIUM,HIGH,URGENT']],
    options: ['OPEN','IN_REVIEW','RESOLVED','CLOSED'],
  },
  refunds: {
    title: 'Yêu cầu hoàn tiền', desc: 'Chỉ xét duyệt yêu cầu. REVIEWED không đồng nghĩa đã hoàn tiền qua cổng.',
    columns: [['id','ID'],['order_code','Mã đơn'],['customer_name','Khách hàng'],['amount','Yêu cầu hoàn'],['status','Trạng thái'],['reason','Lý do']],
    filters: ['PENDING','REVIEWED','REJECTED','EXECUTED'],
    fields: [['orderId','ID đơn hàng','number'],['amount','Số tiền yêu cầu','number'],['reason','Lý do (ít nhất 10 ký tự)','textarea']],
    options: ['REVIEWED','REJECTED'],
    warning: 'Không thực hiện giao dịch refund từ Admin; phải do Refund Service của Tài xác nhận EXECUTED.',
  },
  reviews: {
    title: 'Báo cáo đánh giá', desc: 'Hàng đợi kiểm duyệt. HIDE_REQUESTED chỉ là đề nghị, không tự ẩn review ở marketplace.',
    columns: [['id','ID'],['external_review_id','Mã review'],['reason','Lý do'],['status','Trạng thái'],['created_at','Tạo lúc']],
    filters: ['OPEN','UNDER_REVIEW','HIDE_REQUESTED','DISMISSED'],
    fields: [['externalReviewId','ID review (tùy chọn)','number'],['reason','Lý do (ít nhất 10 ký tự)','textarea']],
    options: ['OPEN','UNDER_REVIEW','HIDE_REQUESTED','DISMISSED'],
    warning: 'Để ẩn review công khai, cần nối command moderation của Thành.',
  },
  tickets: {
    title: 'Hỗ trợ khách hàng', desc: 'Phân loại, ghi chú xử lý và theo dõi ticket.',
    columns: [['id','ID'],['ticket_code','Mã'],['name','Người liên hệ'],['contact_email','Email'],['subject','Tiêu đề'],['priority','Ưu tiên'],['status','Trạng thái']],
    filters: ['OPEN','IN_PROGRESS','ANSWERED','CLOSED'],
    fields: [['name','Tên liên hệ','text'],['contactEmail','Email','text'],['subject','Tiêu đề','text'],['message','Nội dung','textarea'],['priority','Ưu tiên','select','LOW,MEDIUM,HIGH,URGENT']],
    options: ['OPEN','IN_PROGRESS','ANSWERED','CLOSED'],
    warning: 'Trạng thái ANSWERED ghi nhận xử lý nội bộ; chưa tự gửi email nếu chưa kết nối dịch vụ mail.',
  },
  audit: {
    title: 'Nhật ký quản trị', desc: 'Nhật ký không sửa/xóa; bao gồm người thao tác, hành động và thời điểm.',
    columns: [['id','ID'],['actor_id','Admin ID'],['action','Hành động'],['entity_type','Đối tượng'],['entity_id','ID đối tượng'],['details','Ghi chú'],['created_at','Thời gian']],
  },
};

function display(value: unknown) {
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'boolean') return value ? 'Có' : 'Không';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}
function validId(value: unknown): number | null {
  const number = Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : null;
}
function statusError(error: unknown): string {
  return error instanceof Error ? error.message : 'Không thể xử lý yêu cầu.';
}
function toIsoWithTimezone(localValue: string) {
  if (!localValue) return '';
  const date = new Date(localValue);
  if (Number.isNaN(date.valueOf())) return '';
  return date.toISOString();
}

export function AdminOperationsPage() {
  const route = useParams<{ resource: string }>();
  const resource = route.resource as AdminOpsModule;
  const config = CONFIG[resource];
  const [params, setParams] = useSearchParams();
  const q = params.get('q') || '';
  const status = params.get('status') || '';
  const page = Math.max(0, Number(params.get('page') || 0) || 0);
  const [data, setData] = useState<AdminOpsPage | null>(null);
  const [detail, setDetail] = useState<AdminRow | null>(null);
  const [message, setMessage] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [actionStatus, setActionStatus] = useState('');
  const [actionNote, setActionNote] = useState('');
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    if (!config) return;
    let cancelled = false;
    setData(null);
    setMessage('');
    adminOperationsClient.list(resource, { q, status, page, size: 10 })
      .then((result) => {
        if (cancelled) return;
        if (page > 0 && page >= result.totalPages) {
          setParams({ q, status, page: String(Math.max(0, result.totalPages - 1)) }, { replace: true });
          return;
        }
        setData(result);
      })
      .catch((error: unknown) => { if (!cancelled) setMessage(statusError(error)); });
    return () => { cancelled = true; };
  }, [resource, q, status, page, revision, setParams, config]);

  if (!config) return <AdminLayout title="Không tìm thấy module"><div className="card">Module không tồn tại.</div></AdminLayout>;
  const refresh = () => setRevision((v) => v + 1);

  async function showDetail(row: AdminRow) {
    const id = validId(row.id);
    if (!id) return;
    setBusy(true); setDetail(null); setMessage(''); setActionStatus(''); setActionNote('');
    try {
      const result = resource === 'chefs' || resource === 'customers'
        ? await adminOperationsClient.profile(resource, id)
        : await adminOperationsClient.detail(resource, id);
      setDetail(result);
    } catch (error) { setMessage(statusError(error)); }
    finally { setBusy(false); }
  }

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!config.fields || busy) return;
    const body: Record<string, unknown> = {};
    const form = new FormData(event.currentTarget);
    for (const [name, , type] of config.fields) {
      let value = String(form.get(name) ?? '').trim();
      if (type === 'date' && value) value = toIsoWithTimezone(value);
      body[name] = value;
    }
    setBusy(true); setMessage(''); setSuccess('');
    try {
      await adminOperationsClient.create(resource, body);
      setFormOpen(false);
      setSuccess('Đã tạo bản ghi.');
      refresh();
    } catch (error) { setMessage(statusError(error)); }
    finally { setBusy(false); }
  }

  async function applyAction() {
    if (!detail || busy) return;
    const record = (detail.profile && typeof detail.profile === 'object' ? detail.profile : detail) as AdminRow;
    const id = validId(record.id);
    if (!id || !actionStatus) {
      setMessage('Vui lòng chọn hành động.');
      return;
    }
    if (actionNote.trim().length < 5) {
      setMessage('Vui lòng nhập lý do ít nhất 5 ký tự.');
      return;
    }
    if (!window.confirm(`Xác nhận ${actionStatus} cho bản ghi #${id}?`)) return;
    setBusy(true); setMessage(''); setSuccess('');
    try {
      if (resource === 'chefs') {
        if (actionStatus === 'SUSPEND' || actionStatus === 'UNSUSPEND')
          await adminOperationsClient.chefActive(id, actionStatus === 'UNSUSPEND', actionNote.trim());
        else await adminOperationsClient.verifyChef(id, actionStatus, actionNote.trim());
      } else if (resource === 'vouchers') {
        await adminOperationsClient.update(resource, id, { active: actionStatus === 'true', note: actionNote.trim() });
      } else {
        await adminOperationsClient.update(resource, id, { status: actionStatus, note: actionNote.trim() });
      }
      setDetail(null); setActionStatus(''); setActionNote('');
      setSuccess('Đã cập nhật dữ liệu và ghi nhật ký thao tác.');
      refresh();
    } catch (error) { setMessage(statusError(error)); }
    finally { setBusy(false); }
  }
  const actionChoices = resource === 'chefs'
    ? ['APPROVED','REJECTED','MORE_INFO','PENDING','SUSPEND','UNSUSPEND']
    : resource === 'vouchers' ? ['true','false'] : (config.options || []);

  return <AdminLayout title={config.title} lead={config.desc}>
    {config.warning && <p className="notice warm">{config.warning}</p>}
    {message && <div className="auth-message error" role="alert">{message} <button type="button" className="text-link" onClick={refresh}>Thử lại</button></div>}
    {success && <div className="auth-message success" role="status">{success}</div>}
    <form key={`${resource}:${q}:${status}`} className="card row admin-ops-filter" onSubmit={(event) => {
      event.preventDefault();
      const form = new FormData(event.currentTarget);
      setParams({ q: String(form.get('q') || ''), status: String(form.get('status') || ''), page: '0' });
    }}>
      <label className="field">Tìm kiếm
        <input name="q" defaultValue={q} placeholder="Nhập từ khóa" maxLength={150} />
      </label>
      <label className="field">Trạng thái
        <select name="status" defaultValue={status}><option value="">Tất cả</option>
          {(config.filters || (resource === 'vouchers' ? ['true','false'] : [])).map((choice) => <option value={choice} key={choice}>{choice}</option>)}
        </select>
      </label>
      <button className="btn" type="submit">Tìm kiếm</button>
      <button className="btn secondary" type="button" onClick={refresh}>Làm mới</button>
      {config.fields && <button className="btn secondary" type="button" onClick={() => setFormOpen((v) => !v)}>
        {formOpen ? 'Đóng biểu mẫu' : 'Tạo bản ghi'}
      </button>}
    </form>
    {formOpen && config.fields && <form className="card stack-sm" onSubmit={submitCreate}>
      <h3>Thêm {config.title.toLowerCase()}</h3>
      <div className="grid grid-2">
        {config.fields.map(([name, title, type, choices]) => <label className="field" key={name}>{title}
          {type === 'textarea' ? <textarea name={name} rows={3} /> : type === 'select'
            ? <select name={name}>{(choices || '').split(',').map((choice) => <option key={choice} value={choice}>{choice}</option>)}</select>
            : <input name={name} type={type === 'date' ? 'datetime-local' : type} min={type === 'number' ? 0 : undefined} />}
        </label>)}
      </div>
      <button disabled={busy} className="btn" type="submit">{busy ? 'Đang lưu…' : 'Lưu mới'}</button>
    </form>}
    <section className="card stack-sm">
      <div className="between"><h3>Danh sách</h3><span className="muted">{data ? `${data.totalElements} bản ghi` : 'Đang tải...'}</span></div>
      {!data ? (message ? <p>Không tải được dữ liệu.</p> : <p>Đang tải dữ liệu...</p>) : <>
        <div className="table-wrap"><table><thead><tr>
          {config.columns.map(([key, title]) => <th key={key}>{title}</th>)}<th>Chi tiết</th>
        </tr></thead><tbody>
          {data.content.length === 0 && <tr><td colSpan={config.columns.length + 1}>Không có bản ghi phù hợp.</td></tr>}
          {data.content.map((row) => <tr key={String(row.id)}>
            {config.columns.map(([key]) => <td key={key}>{display(row[key])}</td>)}
            <td><button className="text-link" type="button" disabled={busy} onClick={() => void showDetail(row)}>Xem</button></td>
          </tr>)}
        </tbody></table></div>
        <div className="row between">
          <span className="muted">Trang {page + 1}/{Math.max(data.totalPages,1)}</span>
          <div className="row">
            <button className="btn secondary sm" disabled={page === 0} onClick={() => setParams({ q, status, page: String(page - 1) })}>Trước</button>
            <button className="btn secondary sm" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ q, status, page: String(page + 1) })}>Sau</button>
          </div>
        </div>
      </>}
    </section>
    {detail && <section className="card stack-sm" aria-label="Chi tiết bản ghi">
      <div className="between"><h3>Chi tiết</h3><button className="btn secondary sm" onClick={() => setDetail(null)}>Đóng</button></div>
      <div className="table-wrap"><table><tbody>
        {Object.entries(detail).map(([key, value]) => <tr key={key}><th>{key.replaceAll('_', ' ')}</th><td>
          {Array.isArray(value) ? <pre className="admin-ops-json">{JSON.stringify(value,null,2)}</pre> :
            typeof value === 'object' && value !== null ? <pre className="admin-ops-json">{JSON.stringify(value,null,2)}</pre> : display(value)}
        </td></tr>)}
      </tbody></table></div>
      {actionChoices.length > 0 && <div className="stack-sm soft">
        <h4>Hành động quản trị</h4>
        <label className="field">Thao tác
          <select value={actionStatus} disabled={busy} onChange={(event) => setActionStatus(event.target.value)}>
            <option value="">Chọn thao tác</option>
            {actionChoices.map((choice) => <option value={choice} key={choice}>{choice}</option>)}
          </select>
        </label>
        <label className="field">Lý do (bắt buộc; xác minh Chef ít nhất 10 ký tự)
          <textarea value={actionNote} disabled={busy} maxLength={1000} rows={3}
            onChange={(event) => setActionNote(event.target.value)} />
        </label>
        <button className="btn" type="button" disabled={busy || !actionStatus || !actionNote.trim()}
          onClick={() => void applyAction()}>
          {busy ? 'Đang xử lý…' : 'Xác nhận thao tác'}
        </button>
      </div>}
    </section>}
  </AdminLayout>;
}
