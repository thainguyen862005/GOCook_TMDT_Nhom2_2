import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { SellerWorkspace } from "../../components/common/SellerWorkspace";
import { SellerPagination } from "../../components/common/SellerPagination";
import {
  sellerMenuClient,
  sellerMenuError,
} from "../../clients/sellerMenuClient";
import { useDocumentTitle } from "../../hooks/common/useDocumentTitle";
import type { PageResponse } from "../../types/api";
import { sellerMenuLabels, type SellerMenu } from "../../types/sellerMenu";

const money = (amount: string) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
    Number(amount),
  );
export function SellerProductsPage() {
  useDocumentTitle("Thực đơn của tôi | GO Cook");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [category, setCategory] = useState("");
  const [categories, setCategories] = useState<string[]>([]);
  const [result, setResult] = useState<PageResponse<SellerMenu> | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(6);
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    sellerMenuClient
      .options(controller.signal)
      .then((options) => setCategories(options.categories))
      .catch((err) => {
        if (!controller.signal.aborted) setError(sellerMenuError(err));
      });
    return () => controller.abort();
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    const timer = window.setTimeout(() => {
      sellerMenuClient
        .list(
          { q: query.trim(), status, category, page, size: pageSize },
          controller.signal,
        )
        .then((data) => {
          if (!controller.signal.aborted) {
            if (page > 0 && !data.content.length) setPage(page - 1);
            else setResult(data);
          }
        })
        .catch((err) => {
          if (!controller.signal.aborted) setError(sellerMenuError(err));
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, 200);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query, status, category, page, pageSize, reload]);
  async function hide(menu: SellerMenu) {
    setBusyId(menu.id);
    setError("");
    setNotice("");
    try {
      await sellerMenuClient.status(menu, "HIDDEN");
      setNotice(`Đã ẩn “${menu.name}”.`);
      setReload((value) => value + 1);
    } catch (err) {
      setError(sellerMenuError(err));
    } finally {
      setBusyId(null);
    }
  }
  return (
    <SellerWorkspace
      title="Thực đơn của tôi"
      description="Những món ngon mang dấu ấn riêng của căn bếp bạn."
    >
      <div className="chef-demo-banner">
        <strong>Thực đơn của bạn</strong>
        <span>
          Dữ liệu được lưu trên hệ thống. Ẩn thực đơn không xóa lịch sử đặt nấu.
        </span>
      </div>
      {notice && (
        <div className="chef-save-toast success">
          <div role="status">
            <strong>✓ Thành công</strong>
            <p>{notice}</p>
          </div>
          <button
            className="btn secondary sm"
            aria-label="Đóng thông báo"
            onClick={() => setNotice("")}
          >
            ×
          </button>
        </div>
      )}
      {error && (
        <div className="notice warm" role="alert">
          {error}{" "}
          <button
            className="btn secondary sm"
            onClick={() => setReload((value) => value + 1)}
          >
            Tải lại
          </button>
        </div>
      )}
      <section className="card chef-filters" aria-label="Lọc thực đơn">
        <label className="field">
          Tìm thực đơn
          <input
            type="search"
            placeholder="Nhập tên thực đơn…"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="field">
          Trạng thái
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Tất cả trạng thái</option>
            {Object.entries(sellerMenuLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Danh mục
          <select
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Tất cả danh mục</option>
            {categories.map((item) => (
              <option key={item}>{item}</option>
            ))}
          </select>
        </label>
      </section>
      {loading ? (
        <p className="card" role="status">
          Đang tải thực đơn…
        </p>
      ) : (
        !error && (
          <>
            <div className="between">
              <h2>{result?.totalElements ?? 0} thực đơn</h2>
              <span className="small muted">
                Chỉ hiển thị thực đơn thuộc tài khoản của bạn.
              </span>
            </div>
            <div className="chef-menu-grid">
              {result?.content.map((menu) => (
                <article className="card chef-menu-card" key={menu.id}>
                  {menu.imageUrl ? (
                    <img
                      className="chef-menu-photo"
                      src={menu.imageUrl}
                      alt={menu.name}
                    />
                  ) : (
                    <div className="chef-menu-photo chef-placeholder">
                      Chưa có ảnh
                    </div>
                  )}
                  <div className="stack-sm chef-menu-content">
                    <div className="between">
                      <span className="eyebrow">{menu.category}</span>
                      <span
                        className={`badge ${menu.status === "PUBLISHED" ? "" : "gray"}`}
                      >
                        {sellerMenuLabels[menu.status]}
                      </span>
                    </div>
                    <h2>{menu.name}</h2>
                    <p className="muted">{menu.description}</p>
                    <p className="small muted">
                      {menu.minGuests}–{menu.maxGuests} khách ·{" "}
                      {menu.images.length} ảnh
                    </p>
                    <p>
                      <strong className="green">{money(menu.unitPrice)}</strong>
                      <span className="small muted"> / khách</span>
                    </p>
                    <div className="row">
                      <Link
                        className="btn secondary sm"
                        to={`/seller/product-edit?id=${menu.id}`}
                      >
                        Chỉnh sửa
                      </Link>
                      {menu.status !== "HIDDEN" && (
                        <button
                          className="btn ghost sm"
                          disabled={busyId !== null}
                          onClick={() => hide(menu)}
                        >
                          {busyId === menu.id ? "Đang ẩn…" : "Ẩn thực đơn"}
                        </button>
                      )}
                    </div>
                  </div>
                </article>
              ))}
            </div>
            {!result?.content.length && (
              <div className="card chef-empty">
                <h2>
                  {query || status || category
                    ? "Không tìm thấy thực đơn phù hợp"
                    : "Bắt đầu với thực đơn đầu tiên"}
                </h2>
                <p className="muted">
                  {query || status || category
                    ? "Thử từ khóa khác hoặc bỏ bộ lọc."
                    : "Tạo một thực đơn và thêm những hình ảnh món ăn của bạn."}
                </p>
                {query || status || category ? (
                  <button
                    className="btn secondary"
                    onClick={() => {
                      setQuery("");
                      setCategory("");
                      setStatus("");
                      setPage(0);
                    }}
                  >
                    Đặt lại bộ lọc
                  </button>
                ) : (
                  <Link className="btn" to="/seller/product-new">
                    + Thêm thực đơn
                  </Link>
                )}
              </div>
            )}
            <div className="chef-panel-head">
              <label className="field small">Hiển thị mỗi trang
                <select value={pageSize} onChange={event => { setPageSize(Number(event.target.value)); setPage(0); }}>
                  <option value={6}>6 thực đơn</option><option value={12}>12 thực đơn</option>
                </select>
              </label>
              <SellerPagination page={page} totalPages={result?.totalPages ?? 0} onPage={setPage} />
            </div>
          </>
        )
      )}
    </SellerWorkspace>
  );
}
