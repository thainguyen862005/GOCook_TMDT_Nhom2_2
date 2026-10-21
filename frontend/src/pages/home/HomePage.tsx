import { useDocumentTitle } from '../../hooks/common/useDocumentTitle';
import { useSiteSettings } from '../../contexts/SiteSettingsContext';

export function HomePage() {
  const site = useSiteSettings();
  useDocumentTitle(`${site.tagline} | ${site.siteName}`);

  return (
    <>
      <section className={"hero"}>
        <div className={"container"}>
          <div className={"hero-grid"}>
            <div className={"hero-copy"}>
              <div>
                <span className={"badge"}>
                  {"BỮA CƠM NHÀ · TRỌN VẸN YÊU THƯƠNG"}
                </span>
              </div>
              <h1>{site.heroTitle}</h1>
              <p className={"lead"}>{site.heroSubtitle}</p>
              <div className={"row soft"}>
                <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                  <path d={"M7 14a4 4 0 0 1-2-7 4 4 0 0 1 7-2 4 4 0 0 1 7 2 4 4 0 0 1-2 7v6H7z"}></path>
                  <path d={"M7 16h10M10 11v2m4-2v2"}></path>
                </svg>
                <div>
                  <strong>
                    {"Nấu tại bếp nhà, chăm từng món ăn"}
                  </strong>
                  <p className={"small muted"}>
                    {"Đi chợ, chế biến và dọn dẹp trong một lần đặt."}
                  </p>
                </div>
              </div>
              <div className={"row"}>
                <a className={"btn"} href={"/chefs"}>
                  {"Tìm đầu bếp ngay "}
                  <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                    <path d={"M4 12h15m-6-6 6 6-6 6"}></path>
                  </svg>
                </a>
                <a className={"btn secondary"} href={"/request"}>
                  {"Đặt theo ngân sách"}
                </a>
              </div>
              <div className={"trust-row"}>
                <span>
                  <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                    <path d={"M7 14a4 4 0 0 1-2-7 4 4 0 0 1 7-2 4 4 0 0 1 7 2 4 4 0 0 1-2 7v6H7z"}></path>
                    <path d={"M7 16h10M10 11v2m4-2v2"}></path>
                  </svg>
                  {" Đầu bếp nấu tại nhà"}
                </span>
                <span>
                  <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                    <path d={"M3 17h18M5 17a7 7 0 0 1 14 0M12 8V5m-2 0h4M4 21h16"}></path>
                  </svg>
                  {" Nguyên liệu tươi mỗi bữa"}
                </span>
                <span>
                  <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                    <path d={"m5 12 4 4L19 6"}></path>
                  </svg>
                  {" Dọn bếp sau khi nấu"}
                </span>
              </div>
            </div>
            <div className={"hero-photo"}>
              <img className={""} src={site.heroImageUrl} alt={site.heroImageAlt} fetchPriority={"high"} />
              <div className={"float-card top"}>
                <img className={"avatar"} src={"/assets/chef.png"} alt={"Ảnh minh họa đầu bếp nấu ăn tại nhà"} loading={"lazy"} />
                <div>
                  <strong>
                    {"Bếp Cô Mai"}
                  </strong>
                  <p className={"small muted"}>
                    <span className={"amber"}>
                      {"★ 4,9"}
                    </span>
                    {" · 186 đánh giá"}
                  </p>
                </div>
              </div>
              <div className={"float-card"}>
                <span className={"icon-box"}>
                  <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                    <path d={"M3 17h18M5 17a7 7 0 0 1 14 0M12 8V5m-2 0h4M4 21h16"}></path>
                  </svg>
                </span>
                <div>
                  <p className={"eyebrow"}>
                    {"Gợi ý cho tối nay"}
                  </p>
                  <strong>
                    {"Mâm cơm sum vầy"}
                  </strong>
                </div>
                <span className={"price"}>
                  {"120k"}
                  <small>
                    {" / người"}
                  </small>
                </span>
              </div>
            </div>
          </div>
          <form className={"search-bar"} action={"/chefs"} method={"get"}>
            <label className={"field"} htmlFor={"location"}>
              {"Khu vực phục vụ"}
              <select id={"location"} name={"location"}>
                <option>
                  {"Bình Thạnh, TP. Hồ Chí Minh"}
                </option>
                <option>
                  {"Phú Nhuận, TP. Hồ Chí Minh"}
                </option>
                <option>
                  {"Hà Nội"}
                </option>
              </select>
            </label>
            <label className={"field"} htmlFor={"date"}>
              {"Ngày dùng bữa"}
              <input id={"date"} name={"date"} type={"date"} defaultValue={""} placeholder={""} />
            </label>
            <label className={"field"} htmlFor={"guests"}>
              {"Số người dùng bữa"}
              <select id={"guests"} name={"guests"}>
                <option>
                  {"4 người"}
                </option>
                <option>
                  {"2 người"}
                </option>
                <option>
                  {"6 người"}
                </option>
                <option>
                  {"8 người"}
                </option>
              </select>
            </label>
            <button className={"btn"} type={"button"} data-action={"search"} disabled title={"Chức năng sẽ được tích hợp ở giai đoạn tiếp theo"}>
              <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                <circle cx={"10.5"} cy={"10.5"} r={"6.5"}></circle>
                <path d={"m16 16 5 5"}></path>
              </svg>
              {" Tìm đầu bếp "}
            </button>
          </form>
        </div>
      </section>
      <section className={"container section"}>
        <div className={"section-head"}>
          <div>
            <p className={"eyebrow"}>
              {"Bữa ăn theo cách của bạn"}
            </p>
            <h2>
              {"Hai cách đặt, một bữa cơm ấm áp"}
            </h2>
            <p className={"muted"}>
              {"Chọn thực đơn yêu thích hoặc để đầu bếp gợi ý theo ngân sách."}
            </p>
          </div>
          <a className={"text-link"} href={"/request"}>
            {"Tạo yêu cầu riêng "}
            <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
              <path d={"M4 12h15m-6-6 6 6-6 6"}></path>
            </svg>
          </a>
        </div>
        <div className={"grid grid-2"}>
          <article className={"card model-card featured"}>
            <div className={"between"}>
              <span className={"badge"}>
                {"01 · Thực đơn có sẵn"}
              </span>
              <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                <path d={"M3 17h18M5 17a7 7 0 0 1 14 0M12 8V5m-2 0h4M4 21h16"}></path>
              </svg>
            </div>
            <h3>
              {"Chọn món ngon, biết ngay chi phí"}
            </h3>
            <p className={"muted"}>
              {"Mâm cơm được đầu bếp chuẩn bị sẵn. Giá theo người, gồm nguyên liệu, công nấu và dọn bếp."}
            </p>
            <ul className={"check-list"}>
              <li>
                {"Chọn theo khẩu vị và số người"}
              </li>
              <li>
                {"Xem món ăn, đầu bếp và tổng tiền trước khi đặt"}
              </li>
              <li>
                {"Phù hợp bữa cơm gia đình hằng ngày"}
              </li>
            </ul>
            <div className={"soft between"}>
              <span>
                {"Mâm cơm miền Nam · 4 người"}
              </span>
              <strong className={"green"}>
                {"480.000đ"}
              </strong>
            </div>
            <a className={"btn full"} href={"/menus"}>
              {"Khám phá thực đơn "}
              <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                <path d={"M4 12h15m-6-6 6 6-6 6"}></path>
              </svg>
            </a>
          </article>
          <article className={"card model-card"}>
            <div className={"between"}>
              <span className={"badge gold"}>
                {"02 · Theo ngân sách"}
              </span>
              <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                <path d={"M7 14a4 4 0 0 1-2-7 4 4 0 0 1 7-2 4 4 0 0 1 7 2 4 4 0 0 1-2 7v6H7z"}></path>
                <path d={"M7 16h10M10 11v2m4-2v2"}></path>
              </svg>
            </div>
            <h3>
              {"Bữa ăn riêng, đúng ý gia đình"}
            </h3>
            <p className={"muted"}>
              {"Chia sẻ món muốn ăn, khẩu vị và ngân sách. Nhận đề xuất thực đơn phù hợp từ đầu bếp."}
            </p>
            <ul className={"check-list"}>
              <li>
                {"Ghi chú dị ứng và nhu cầu riêng"}
              </li>
              <li>
                {"Chủ động khoảng ngân sách"}
              </li>
              <li>
                {"So sánh báo giá trước khi chọn"}
              </li>
            </ul>
            <div className={"soft between"}>
              <span>
                {"Bạn đặt ngân sách"}
              </span>
              <strong className={"amber"}>
                {"Đầu bếp lên thực đơn"}
              </strong>
            </div>
            <a className={"btn secondary full"} href={"/request"}>
              {"Tạo yêu cầu nấu ăn "}
              <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                <path d={"M4 12h15m-6-6 6 6-6 6"}></path>
              </svg>
            </a>
          </article>
        </div>
      </section>
      <section className={"container section"}>
        <div className={"section-head"}>
          <div>
            <p className={"eyebrow"}>
              {"Gặp người giữ lửa"}
            </p>
            <h2>
              {"Một gian bếp, nhiều điều tận tâm"}
            </h2>
            <p className={"muted"}>
              {"Tìm đầu bếp phù hợp với hương vị gia đình bạn."}
            </p>
          </div>
          <a className={"text-link"} href={"/chefs"}>
            {"Khám phá đầu bếp "}
            <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
              <path d={"M4 12h15m-6-6 6 6-6 6"}></path>
            </svg>
          </a>
        </div>
        <div className={"grid grid-2"}>
          <article className={"card chef-card"}>
            <img className={""} src={"/assets/chef.png"} alt={"Ảnh minh họa đầu bếp nấu ăn tại nhà"} loading={"lazy"} />
            <div className={"card-body"}>
              <div className={"between"}>
                <span className={"badge"}>
                  {"Đầu bếp nổi bật"}
                </span>
                <span className={"small amber"}>
                  {"★ 4,9 "}
                  <span className={"muted"}>
                    {"(186 đánh giá)"}
                  </span>
                </span>
              </div>
              <div>
                <h3>
                  {"Bếp Cô Mai"}
                </h3>
                <p className={"small muted"}>
                  {"Nguyễn Thị Mai · 12 năm kinh nghiệm"}
                </p>
              </div>
              <p className={"small"}>
                {"Chăm chút hương vị miền Nam, từ nồi cá kho đến bát canh chua quen thuộc."}
              </p>
              <div className={"row small muted"}>
                <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                  <path d={"M19 10c0 6-7 11-7 11S5 16 5 10a7 7 0 0 1 14 0z"}></path>
                  <circle cx={"12"} cy={"10"} r={"2"}></circle>
                </svg>
                {" Bình Thạnh · Phú Nhuận "}
                <span className={"badge gray"}>
                  {"Có lịch trống"}
                </span>
              </div>
              <div className={"between"}>
                <span className={"price"}>
                  {"120.000đ"}
                  <small>
                    {" / người"}
                  </small>
                </span>
                <a className={"btn sm"} href={"/chef-detail"}>
                  {"Xem gian bếp"}
                </a>
              </div>
            </div>
          </article>
          <article className={"card chef-card"}>
            <div className={"initial-portrait"} aria-label={"Ảnh đầu bếp chưa cập nhật"}>
              {"AN"}
            </div>
            <div className={"card-body"}>
              <div className={"between"}>
                <span className={"badge"}>
                  {"Chuyên món chay"}
                </span>
                <span className={"small amber"}>
                  {"★ 4,8 "}
                  <span className={"muted"}>
                    {"(64 đánh giá)"}
                  </span>
                </span>
              </div>
              <div>
                <h3>
                  {"Bếp An Nhiên"}
                </h3>
                <p className={"small muted"}>
                  {"Bếp chay gia đình · 6 năm kinh nghiệm"}
                </p>
              </div>
              <p className={"small"}>
                {"Thực đơn rau củ theo mùa, nhẹ nhàng và cân bằng."}
              </p>
              <div className={"row small muted"}>
                <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                  <path d={"M19 10c0 6-7 11-7 11S5 16 5 10a7 7 0 0 1 14 0z"}></path>
                  <circle cx={"12"} cy={"10"} r={"2"}></circle>
                </svg>
                {" Bình Thạnh · Phú Nhuận "}
                <span className={"badge gray"}>
                  {"Có lịch trống"}
                </span>
              </div>
              <div className={"between"}>
                <span className={"price"}>
                  {"105.000đ"}
                  <small>
                    {" / người"}
                  </small>
                </span>
                <a className={"btn sm"} href={"/chef-an-nhien"}>
                  {"Xem gian bếp"}
                </a>
              </div>
            </div>
          </article>
        </div>
      </section>
      <section className={"container section"}>
        <div className={"section-head"}>
          <div>
            <p className={"eyebrow"}>
              {"Thực đơn bán chạy"}
            </p>
            <h2>
              {"Hôm nay, nhà mình ăn gì?"}
            </h2>
            <p className={"muted"}>
              {"Những bữa cơm quen mà ngon, được nấu nóng ngay tại nhà."}
            </p>
          </div>
          <a className={"text-link"} href={"/menus"}>
            {"Xem tất cả "}
            <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
              <path d={"M4 12h15m-6-6 6 6-6 6"}></path>
            </svg>
          </a>
        </div>
        <div className={"grid grid-3"}>
          <article className={"card media-card"}>
            <a href={"/menu-detail"} className={"media"}>
              <img className={""} src={site.promoImageUrl || "/assets/meal.png"} alt={"Mâm cơm Việt với cá kho, canh chua, rau xào và thịt luộc"} loading={"lazy"} />
              <span className={"badge"}>
                {"Món miền Nam"}
              </span>
            </a>
            <div className={"card-body"}>
              <div className={"between"}>
                <span className={"small muted"}>
                  {"Bếp Cô Mai · 4 món"}
                </span>
                <span className={"small amber"}>
                  {"★ 4,9"}
                </span>
              </div>
              <h3>
                <a href={"/menu-detail"}>
                  {"Mâm Cơm Sum Vầy Miền Nam"}
                </a>
              </h3>
              <p className={"small muted"}>
                {"Cá kho tộ · Canh chua tôm · Rau muống xào · Thịt luộc"}
              </p>
              <div className={"between"}>
                <span className={"price"}>
                  {"120.000đ"}
                  <small>
                    {" / người"}
                  </small>
                </span>
                <span className={"small muted"}>
                  {"4 người"}
                </span>
              </div>
              <a className={"btn secondary full"} href={"/menu-detail"}>
                {"Xem thực đơn"}
              </a>
            </div>
          </article>
          <article className={"card media-card"}>
            <a href={"/menu-vegetarian"} className={"media"}>
              <img className={""} src={"/assets/vegetarian.png"} alt={"Mâm cơm chay với đậu hũ, canh nấm, rau xào và gỏi cuốn"} loading={"lazy"} />
              <span className={"badge"}>
                {"Món chay"}
              </span>
            </a>
            <div className={"card-body"}>
              <div className={"between"}>
                <span className={"small muted"}>
                  {"Bếp Cô Mai · 4 món"}
                </span>
                <span className={"small amber"}>
                  {"★ 4,9"}
                </span>
              </div>
              <h3>
                <a href={"/menu-vegetarian"}>
                  {"Mâm Chay Thanh Lành"}
                </a>
              </h3>
              <p className={"small muted"}>
                {"Đậu hũ sốt cà · Canh nấm · Rau xào · Gỏi cuốn"}
              </p>
              <div className={"between"}>
                <span className={"price"}>
                  {"105.000đ"}
                  <small>
                    {" / người"}
                  </small>
                </span>
                <span className={"small muted"}>
                  {"4 người"}
                </span>
              </div>
              <a className={"btn secondary full"} href={"/menu-vegetarian"}>
                {"Xem thực đơn"}
              </a>
            </div>
          </article>
          <article className={"card model-card"}>
            <span className={"icon-box"}>
              <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                <path d={"M7 14a4 4 0 0 1-2-7 4 4 0 0 1 7-2 4 4 0 0 1 7 2 4 4 0 0 1-2 7v6H7z"}></path>
                <path d={"M7 16h10M10 11v2m4-2v2"}></path>
              </svg>
            </span>
            <p className={"eyebrow"}>
              {"Một chút riêng cho nhà bạn"}
            </p>
            <h3>
              {"Chưa thấy món mình thích?"}
            </h3>
            <p className={"muted"}>
              {"Kể với đầu bếp món bạn đang thèm. Một thực đơn riêng đang chờ bạn."}
            </p>
            <a className={"btn secondary"} href={"/request"}>
              {"Tạo thực đơn riêng "}
              <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
                <path d={"M4 12h15m-6-6 6 6-6 6"}></path>
              </svg>
            </a>
          </article>
        </div>
      </section>
      <section className={"container section"}>
        <div className={"section-head"}>
          <div>
            <p className={"eyebrow"}>
              {"Đơn giản từ lần đầu"}
            </p>
            <h2>
              {"Bốn bước để có một bữa cơm ngon"}
            </h2>
            <p className={"muted"}></p>
          </div>
          <a className={"text-link"} href={"/help"}>
            {"Xem hướng dẫn "}
            <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
              <path d={"M4 12h15m-6-6 6 6-6 6"}></path>
            </svg>
          </a>
        </div>
        <div className={"grid grid-4 steps"}>
          <article className={"card step-card"}>
            <h3>
              {"Chọn bữa ăn"}
            </h3>
            <p className={"muted small"}>
              {"Tìm đầu bếp hoặc thực đơn hợp khẩu vị."}
            </p>
          </article>
          <article className={"card step-card"}>
            <h3>
              {"Đặt lịch"}
            </h3>
            <p className={"muted small"}>
              {"Chọn ngày, số người và địa chỉ nấu."}
            </p>
          </article>
          <article className={"card step-card"}>
            <h3>
              {"Thảnh thơi chờ"}
            </h3>
            <p className={"muted small"}>
              {"Đầu bếp đi chợ và nấu tại bếp nhà."}
            </p>
          </article>
          <article className={"card step-card"}>
            <h3>
              {"Cùng thưởng thức"}
            </h3>
            <p className={"muted small"}>
              {"Dùng bữa, nghiệm thu và góp ý."}
            </p>
          </article>
        </div>
      </section>
      <section className={"container section"}>
        <div className={"grid grid-2"}>
          <div>
            <div className={"section-head"}>
              <div>
                <p className={"eyebrow"}>
                  {"Vừa lên kệ bếp"}
                </p>
                <h2>
                  {"Thực đơn mới nhất"}
                </h2>
              </div>
            </div>
            <article className={"card media-card"}>
              <a href={"/menu-vegetarian"} className={"media"}>
                <img className={""} src={"/assets/vegetarian.png"} alt={"Mâm cơm chay với đậu hũ, canh nấm, rau xào và gỏi cuốn"} loading={"lazy"} />
                <span className={"badge"}>
                  {"Món chay"}
                </span>
              </a>
              <div className={"card-body"}>
                <div className={"between"}>
                  <span className={"small muted"}>
                    {"Bếp Cô Mai · 4 món"}
                  </span>
                  <span className={"small amber"}>
                    {"★ 4,9"}
                  </span>
                </div>
                <h3>
                  <a href={"/menu-vegetarian"}>
                    {"Mâm Chay Thanh Lành"}
                  </a>
                </h3>
                <p className={"small muted"}>
                  {"Đậu hũ sốt cà · Canh nấm · Rau xào · Gỏi cuốn"}
                </p>
                <div className={"between"}>
                  <span className={"price"}>
                    {"105.000đ"}
                    <small>
                      {" / người"}
                    </small>
                  </span>
                  <span className={"small muted"}>
                    {"4 người"}
                  </span>
                </div>
                <a className={"btn secondary full"} href={"/menu-vegetarian"}>
                  {"Xem thực đơn"}
                </a>
              </div>
            </article>
          </div>
          <div>
            <div className={"section-head"}>
              <div>
                <p className={"eyebrow"}>
                  {"Được quan tâm"}
                </p>
                <h2>
                  {"Xem nhiều nhất"}
                </h2>
              </div>
            </div>
            <article className={"card media-card"}>
              <a href={"/menu-detail"} className={"media"}>
                <img className={""} src={"/assets/meal.png"} alt={"Mâm cơm Việt với cá kho, canh chua, rau xào và thịt luộc"} loading={"lazy"} />
                <span className={"badge"}>
                  {"Món miền Nam"}
                </span>
              </a>
              <div className={"card-body"}>
                <div className={"between"}>
                  <span className={"small muted"}>
                    {"Bếp Cô Mai · 4 món"}
                  </span>
                  <span className={"small amber"}>
                    {"★ 4,9"}
                  </span>
                </div>
                <h3>
                  <a href={"/menu-detail"}>
                    {"Mâm Cơm Sum Vầy Miền Nam"}
                  </a>
                </h3>
                <p className={"small muted"}>
                  {"Cá kho tộ · Canh chua tôm · Rau muống xào · Thịt luộc"}
                </p>
                <div className={"between"}>
                  <span className={"price"}>
                    {"120.000đ"}
                    <small>
                      {" / người"}
                    </small>
                  </span>
                  <span className={"small muted"}>
                    {"4 người"}
                  </span>
                </div>
                <a className={"btn secondary full"} href={"/menu-detail"}>
                  {"Xem thực đơn"}
                </a>
              </div>
            </article>
          </div>
        </div>
      </section>
      <section className={"container section"}>
        <div className={"dark-banner between"}>
          <div>
            <p className={"eyebrow"}>
              {"Dành cho người yêu bếp"}
            </p>
            <h2>
              {"Biến đam mê nấu nướng"}
              <br />
              {"thành những bữa cơm ý nghĩa."}
            </h2>
            <p>
              {"Gia nhập GO Cook và mang hương vị của bạn đến nhiều gia đình."}
            </p>
          </div>
          <a className={"btn secondary"} href={"/become-chef"}>
            {"Trở thành đầu bếp "}
            <svg className={"icon"} viewBox={"0 0 24 24"} aria-hidden={"true"}>
              <path d={"M4 12h15m-6-6 6 6-6 6"}></path>
            </svg>
          </a>
        </div>
      </section>
    </>
  );
}
