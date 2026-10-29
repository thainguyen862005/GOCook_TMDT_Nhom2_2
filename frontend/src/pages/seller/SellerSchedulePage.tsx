import { Link } from 'react-router-dom';
import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { SellerWorkspace } from "../../components/common/SellerWorkspace";
import {
  sellerScheduleClient,
  sellerScheduleError,
} from "../../clients/sellerScheduleClient";
import { useDocumentTitle } from "../../hooks/common/useDocumentTitle";
import type { SellerSchedule, SellerSlot } from "../../types/sellerSchedule";
import {
  canManageSlot,
  nextStartTime,
  vietnamNow,
} from "../../util/scheduleTime";

function dayOffset(date: string, count: number) {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() + count);
  return value.toISOString().slice(0, 10);
}
function today() {
  return vietnamNow().slice(0, 10);
}
function validDate(value: string) {
  return (
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    value >= "2000-01-01" &&
    value <= "2099-12-31" &&
    !Number.isNaN(Date.parse(value)) &&
    dayOffset(value, 0) === value
  );
}
function displayDate(value: string) {
  return value.split("-").reverse().join("/");
}
function overlapsDay(start: string, end: string, date: string) {
  return start < `${dayOffset(date, 1)}T00:00:00` && end > `${date}T00:00:00`;
}

export function SellerSchedulePage() {
  useDocumentTitle("Lịch phục vụ | GO Cook");
  const [params, setParams] = useSearchParams();
  const [now, setNow] = useState(() => vietnamNow());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(vietnamNow()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const paramDate = params.get("date") || "";
  const date = validDate(paramDate) ? paramDate : today();
  const lastDay =
    dayOffset(date, 6) > "2099-12-31" ? "2099-12-31" : dayOffset(date, 6);
  const [data, setData] = useState<SellerSchedule | null>(null);
  const [loadedDate, setLoadedDate] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [reload, setReload] = useState(0);
  const [start, setStart] = useState("09:00");
  const [end, setEnd] = useState("12:00");
  const [available, setAvailable] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [removeId, setRemoveId] = useState<number | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setData(null);
    setLoadedDate("");
    setError("");
    setRemoveId(null);
    sellerScheduleClient
      .list(date, lastDay, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setData(result);
          setLoadedDate(date);
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted) setError(sellerScheduleError(err));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [date, lastDay, reload]);
  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(""), 6000);
    return () => window.clearTimeout(timer);
  }, [notice]);
  const ready = !loading && loadedDate === date && data !== null;
  const pastDay = date < now.slice(0, 10);
  const canCreate = canManageSlot(date, "23:59", now);
  const daySlots = ready
    ? data.slots.filter((slot) => slot.serviceDate === date)
    : [];
  const bookings = ready
    ? data.bookings.filter((b) => overlapsDay(b.startsAt, b.endsAt, date))
    : [];
  async function action(callback: () => Promise<unknown>, message: string) {
    if (pending.current || !ready) return;
    pending.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await callback();
      setNotice(message);
      setRemoveId(null);
      setData(null);
      setLoadedDate("");
      setReload((value) => value + 1);
    } catch (err) {
      setError(sellerScheduleError(err));
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  function navigateDay(value: string) {
    if (!busy && validDate(value)) setParams({ date: value });
  }
  function toggle(slot: SellerSlot) {
    if (!canManageSlot(slot.serviceDate, slot.startTime)) {
      setError("Khung giờ đã bắt đầu chỉ được xem, không thể thay đổi.");
      return;
    }
    void action(
      () => sellerScheduleClient.status(slot),
      "Đã cập nhật trạng thái khung giờ.",
    );
  }
  return (
    <SellerWorkspace
      title="Lịch phục vụ"
      description="Chủ động thời gian, dành trọn tâm sức cho từng bữa ăn."
    >
      <div className="chef-demo-banner">
        <strong>Lịch phục vụ · Giờ Việt Nam</strong>
        <span>
          Lịch được lưu cho tài khoản của bạn. Khung giờ liền nhau được phép.
        </span>
      </div>
      {notice && (
        <div className="chef-save-toast success" role="status">
          <span>{notice}</span>
          <button
            type="button"
            className="btn ghost sm"
            aria-label="Đóng thông báo"
            onClick={() => setNotice("")}
          >
            ×
          </button>
        </div>
      )}
      <section className="card stack">
        <div className="between chef-section-heading">
          <div>
            <p className="eyebrow">Kế hoạch của bạn</p>
            <h2>
              {displayDate(date)} — {displayDate(lastDay)}
            </h2>
          </div>
          <div className="row">
            <button
              className="btn secondary sm"
              disabled={busy || dayOffset(date, -7) < "2000-01-01"}
              aria-label="7 ngày trước"
              onClick={() => navigateDay(dayOffset(date, -7))}
            >
              ←
            </button>
            <button
              className="btn secondary sm"
              disabled={busy}
              onClick={() => navigateDay(today())}
            >
              Hôm nay
            </button>
            <button
              className="btn secondary sm"
              disabled={busy || dayOffset(date, 7) > "2099-12-31"}
              aria-label="7 ngày sau"
              onClick={() => navigateDay(dayOffset(date, 7))}
            >
              →
            </button>
          </div>
        </div>
        <div className="chef-week">
          {Array.from(
            {
              length: Math.min(
                7,
                Math.round(
                  (Date.parse(lastDay) - Date.parse(date)) / 86400000,
                ) + 1,
              ),
            },
            (_, index) => {
              const day = dayOffset(date, index);
              const count = ready
                ? data.bookings.filter((b) =>
                    overlapsDay(b.startsAt, b.endsAt, day),
                  ).length
                : 0;
              const open =
                ready &&
                data.slots.some((s) => s.serviceDate === day && s.available);
              return (
                <button
                  disabled={busy}
                  className={`chef-day ${index === 0 ? "is-today" : ""}`}
                  key={day}
                  aria-pressed={index === 0}
                  onClick={() => navigateDay(day)}
                >
                  <span>
                    {new Date(`${day}T12:00:00Z`).toLocaleDateString("vi-VN", {
                      weekday: "short",
                      timeZone: "UTC",
                    })}
                  </span>
                  <strong>{day.slice(8)}</strong>
                  <small>
                    {!ready
                      ? "—"
                      : count
                        ? `${count} lịch đã nhận`
                        : open
                          ? "Đang mở lịch"
                          : "Chưa mở lịch"}
                  </small>
                </button>
              );
            },
          )}
        </div>
      </section>
      {loading && (
        <p className="notice" role="status">
          Đang tải lịch phục vụ...
        </p>
      )}
      {error && (
        <div className="notice warm" role="alert">
          <p>{error}</p>
          <button
            className="btn secondary sm"
            disabled={busy || loading}
            onClick={() => setReload((value) => value + 1)}
          >
            Tải lại lịch
          </button>
        </div>
      )}
      <div className="chef-detail-grid">
        <section className="card stack">
          <div>
            <p className="eyebrow">Khung giờ nhận nấu</p>
            <h2>Thiết lập lịch</h2>
          </div>
          <form
            className="stack-sm"
            onSubmit={(event) => {
              event.preventDefault();
              if (!canManageSlot(date, start)) {
                setError(
                  "Chọn khung giờ bắt đầu sau thời điểm hiện tại (giờ Việt Nam).",
                );
                return;
              }
              if (start >= end) {
                setError("Giờ kết thúc phải sau giờ bắt đầu trong cùng ngày.");
                return;
              }
              void action(
                () =>
                  sellerScheduleClient.create({
                    serviceDate: date,
                    startTime: start,
                    endTime: end,
                    available,
                  }),
                "Đã lưu khung giờ.",
              );
            }}
          >
            <label className="field">
              Ngày đang xem
              <input
                required
                disabled={busy}
                type="date"
                min="2000-01-01"
                max="2099-12-31"
                value={date}
                onChange={(event) => navigateDay(event.target.value)}
              />
            </label>
            {!canCreate && (
              <p className="notice" role="status">
                {pastDay
                  ? "Ngày đã qua: chỉ xem lịch sử, không thể thêm hoặc thay đổi lịch."
                  : "Hôm nay không còn khung giờ bắt đầu hợp lệ. Vui lòng chọn ngày tiếp theo."}
              </p>
            )}
            <fieldset
              className="stack-sm"
              disabled={busy || !ready || !canCreate}
            >
              <div className="grid grid-2">
                <label className="field">
                  Từ giờ
                  <input
                    required
                    type="time"
                    min={nextStartTime(date, now)}
                    step="60"
                    value={start}
                    onChange={(event) => setStart(event.target.value)}
                  />
                </label>
                <label className="field">
                  Đến giờ
                  <input
                    required
                    type="time"
                    step="60"
                    value={end}
                    onChange={(event) => setEnd(event.target.value)}
                  />
                </label>
              </div>
              <label className="row">
                <input
                  type="checkbox"
                  checked={available}
                  onChange={(event) => setAvailable(event.target.checked)}
                />{" "}
                Nhận đơn trong khung giờ này
              </label>
              <button className="btn">
                {busy ? "Đang lưu..." : "Lưu khung giờ"}
              </button>
            </fieldset>
            <p className="small muted">
              Chỉ tạo lịch có giờ bắt đầu sau hiện tại theo giờ Việt Nam. Không
              tạo khung chồng lên lịch đã có. Muốn đổi giờ, xóa khung chưa có
              đơn rồi tạo lại. Khung có đơn đã nhận không thể đóng hoặc xóa.
            </p>
          </form>
        </section>
        <section className="card stack">
          <h2>Khung giờ ngày {displayDate(date)}</h2>
          {daySlots.map((slot) => (
            <article className="soft stack-sm" key={slot.id}>
              <div className="between">
                <strong>
                  {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}
                </strong>
                <span className={`badge ${slot.available ? "" : "gray"}`}>
                  {slot.available ? "Mở nhận nấu" : "Đóng nhận nấu"}
                </span>
              </div>
              <div className="row">
                <button
                  className="btn secondary sm"
                  disabled={
                    busy ||
                    !ready ||
                    !canManageSlot(slot.serviceDate, slot.startTime, now)
                  }
                  onClick={() => toggle(slot)}
                >
                  {slot.available ? "Đóng khung giờ" : "Mở khung giờ"}
                </button>
                <button
                  className="btn ghost sm"
                  disabled={
                    busy ||
                    !ready ||
                    !canManageSlot(slot.serviceDate, slot.startTime, now)
                  }
                  onClick={() => setRemoveId(slot.id)}
                >
                  Xóa
                </button>
              </div>
              {!canManageSlot(slot.serviceDate, slot.startTime, now) && (
                <p className="small muted">Khung giờ đã bắt đầu · Chỉ xem</p>
              )}
              {removeId === slot.id && (
                <div className="row">
                  <span className="small">Xóa khung giờ này?</span>
                  <button
                    className="btn secondary sm"
                    disabled={
                      busy ||
                      !ready ||
                      !canManageSlot(slot.serviceDate, slot.startTime, now)
                    }
                    onClick={() =>
                      void action(
                        () => sellerScheduleClient.remove(slot),
                        "Đã xóa khung giờ.",
                      )
                    }
                  >
                    Xác nhận
                  </button>
                  <button
                    className="btn ghost sm"
                    disabled={busy}
                    onClick={() => setRemoveId(null)}
                  >
                    Hủy
                  </button>
                </div>
              )}
            </article>
          ))}
          {ready && !daySlots.length && (
            <p className="muted">
              Chưa có khung giờ. Thêm lịch để có thể nhận đơn trong ngày này.
            </p>
          )}
        </section>
      </div>
      <section className="card stack">
        <div className="between">
          <h2>Lịch đang giữ chỗ</h2>
          <span className="badge gray">
            {ready ? `${bookings.length} đơn` : "—"}
          </span>
        </div>
        {bookings.map((booking) => (
          <article className="chef-booking" key={booking.id}>
            <span className="chef-time">
              {displayDate(booking.startsAt.slice(0, 10))}{" "}
              {booking.startsAt.slice(11, 16)} —{" "}
              {displayDate(booking.endsAt.slice(0, 10))}{" "}
              {booking.endsAt.slice(11, 16)}
            </span>
            <strong>Đơn #{booking.orderId}</strong>
            <span className="badge gold">Giữ lịch (xem trạng thái tiền trong đơn)</span>
            <Link className="btn secondary sm" to={`/seller/order-detail?id=${booking.orderId}`}>Kiểm tra đơn</Link>
          </article>
        ))}
        {ready && !bookings.length && (
          <p className="muted">Chưa có lịch giữ chỗ trong ngày này.</p>
        )}
        <p className="small muted">
          Lịch giữ chỗ có thể chưa thanh toán. Chỉ nhận nấu khi trạng thái VNPay đã được xác minh và escrow HELD; hãy kiểm tra trong Chi tiết đơn.
        </p>
      </section>
    </SellerWorkspace>
  );
}
