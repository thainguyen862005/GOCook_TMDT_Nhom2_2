package com.james.LMS.seller.orders;

import com.james.LMS.enums.OrderStatus;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Chef-side order commands. Only the Chef's authenticated user ID is trusted.
 * This service does not capture payments, refund money, or release escrow.
 * The database exclusion constraint on chef_bookings is a second line of defense
 * against overlapping reservations when other modules are processing bookings.
 */
@Service
public class ChefOrderService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private final JdbcTemplate jdbc;

  public ChefOrderService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record OrderRow(long id, long version, String orderCode, String customerName,
      String productName, String serviceType, String customerPhone, String serviceAddress,
      String customerNote, int guestCount, LocalDateTime scheduledAt, int durationMinutes,
      BigDecimal totalAmount, String status, String escrowStatus, String rejectedReason,
      Long createdAt, LocalDateTime chefCompletedAt) {}
  public record OrderPage(List<OrderRow> content, int page, int size, long totalElements,
      long totalPages) {}
  public record HistoryRow(String previousStatus, String newStatus, String note,
      LocalDateTime changedAt) {}
  public record ReceiptRow(long id, String filename, String mimeType, String note,
      String fileUrl) {}
  public record OrderDetail(OrderRow order, List<HistoryRow> history, List<ReceiptRow> receipts) {}
  public record Summary(long pending, long active, long awaitingAcceptance,
      long completed, long today, List<OrderRow> recent) {}
  public record Transition(String expectedStatus, String targetStatus, Long version, String reason) {}

  private static final String SELECT = """
      SELECT o.id, o.version, o.order_code, o.customer_name, o.product_name,
             o.service_type, o.customer_phone, o.service_address, o.customer_note,
             o.guest_count, o.scheduled_at, o.service_duration_minutes,
             o.total_amount, o.status, o.rejected_reason, o.created_at,
             o.chef_completed_at, e.status AS escrow_status
      FROM orders o LEFT JOIN order_escrows e ON e.order_id=o.id
      WHERE o.chef_id=? AND o.is_active=true
      """;

  private final org.springframework.jdbc.core.RowMapper<OrderRow> mapper = (rs, n) ->
      new OrderRow(rs.getLong("id"),rs.getLong("version"),rs.getString("order_code"),
          rs.getString("customer_name"),rs.getString("product_name"),
          rs.getString("service_type"),rs.getString("customer_phone"),
          rs.getString("service_address"),rs.getString("customer_note"),
          rs.getInt("guest_count"),rs.getTimestamp("scheduled_at").toLocalDateTime(),
          rs.getInt("service_duration_minutes"),rs.getBigDecimal("total_amount"),
          rs.getString("status"),rs.getString("escrow_status"),
          rs.getString("rejected_reason"),rs.getLong("created_at"),
          rs.getTimestamp("chef_completed_at") == null ? null :
              rs.getTimestamp("chef_completed_at").toLocalDateTime());

  @Transactional(readOnly = true)
  public OrderPage list(long chef, String status, int page, int size, LocalDate from, LocalDate to) {
    if (page < 0 || page > 100000 || size < 1 || size > 50
        || (from != null && to != null && to.isBefore(from)))
      throw error(HttpStatus.BAD_REQUEST, "Bộ lọc hoặc phân trang không hợp lệ.");
    // Reject malformed filters before any database lookup.
    String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    if (!normalized.isEmpty()) parse(normalized);
    requireVerifiedChef(chef);
    String filter = (normalized.isEmpty() ? "" : " AND o.status=?")
        + (from == null ? "" : " AND o.scheduled_at >= ?")
        + (to == null ? "" : " AND o.scheduled_at < ?");
    var args = new java.util.ArrayList<Object>();
    args.add(chef);
    if (!normalized.isEmpty()) args.add(normalized);
    if (from != null) args.add(Timestamp.valueOf(from.atStartOfDay()));
    if (to != null) args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
    Long total = jdbc.queryForObject("SELECT count(*) FROM orders o WHERE o.chef_id=? AND o.is_active=true"+filter,
        Long.class,args.toArray());
    var paging = new java.util.ArrayList<>(args);
    paging.add(size);
    paging.add((long)page*size);
    var rows = jdbc.query(SELECT+filter+" ORDER BY o.scheduled_at DESC,o.id DESC LIMIT ? OFFSET ?",
        mapper,paging.toArray());
    long count=total == null ? 0 : total;
    return new OrderPage(rows,page,size,count,(count+size-1)/size);
  }

  @Transactional(readOnly = true)
  public Summary summary(long chef) {
    requireVerifiedChef(chef);
    var counts = jdbc.queryForMap("""
        SELECT COUNT(*) FILTER (WHERE status='PENDING') AS pending,
               COUNT(*) FILTER (WHERE status IN ('CONFIRMED','PREPARING','COOKING')) AS active,
               COUNT(*) FILTER (WHERE status='AWAITING_ACCEPTANCE') AS awaiting,
               COUNT(*) FILTER (WHERE status='COMPLETED') AS completed,
               COUNT(*) FILTER (WHERE status IN ('CONFIRMED','PREPARING','COOKING')
                 AND scheduled_at >= ? AND scheduled_at < ?) AS today
        FROM orders WHERE chef_id=? AND is_active=true
        """,Timestamp.valueOf(LocalDate.now(ZONE).atStartOfDay()),
        Timestamp.valueOf(LocalDate.now(ZONE).plusDays(1).atStartOfDay()),chef);
    return new Summary(number(counts.get("pending")),number(counts.get("active")),
        number(counts.get("awaiting")),number(counts.get("completed")),
        number(counts.get("today")),list(chef,"",0,5,null,null).content());
  }

  private void requireVerifiedChef(long chef) {
    Boolean allowed=jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM users u JOIN chef_profiles cp ON cp.user_id=u.id
        WHERE u.id=? AND u.is_active=true AND cp.is_verified=true AND cp.is_active=true)
        """,Boolean.class,chef);
    if (!Boolean.TRUE.equals(allowed))
      throw error(HttpStatus.FORBIDDEN,"Đầu bếp cần được duyệt và đang hoạt động để xem đơn đặt nấu.");
  }

  private static long number(Object value) { return ((Number)value).longValue(); }

  @Transactional(readOnly = true)
  public OrderDetail get(long chef, long id) {
    requireVerifiedChef(chef);
    var rows = jdbc.query(SELECT+" AND o.id=?",mapper,chef,id);
    if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy đơn đặt nấu.");
    var history = jdbc.query("""
        SELECT previous_status,new_status,note,changed_at FROM order_status_history
        WHERE order_id=? ORDER BY changed_at,id
        """,(rs,n) -> new HistoryRow(rs.getString(1),rs.getString(2),rs.getString(3),
            rs.getTimestamp(4).toLocalDateTime()),id);
    var receipts = jdbc.query("""
        SELECT id,original_filename,mime_type,note,file_url FROM order_ingredient_receipts
        WHERE chef_id=? AND order_id=? AND is_active=true ORDER BY created_at DESC,id DESC
        """,(rs,n) -> new ReceiptRow(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),chef,id);
    return new OrderDetail(rows.get(0),history,receipts);
  }

  @Transactional
  public OrderDetail transition(long chef, long id, Transition request) {
    if (request == null || request.version() == null || request.version() < 0)
      throw error(HttpStatus.BAD_REQUEST,"Thiếu phiên bản đơn hàng.");
    OrderStatus expected=parse(request.expectedStatus());
    OrderStatus target=parse(request.targetStatus());
    if (expected == target) throw error(HttpStatus.BAD_REQUEST,"Trạng thái không thay đổi.");
    if (!expected.canTransitionTo(target)) throw error(HttpStatus.CONFLICT,"Chuyển trạng thái không hợp lệ.");
    if (!List.of(OrderStatus.CONFIRMED,OrderStatus.PREPARING,OrderStatus.COOKING,
        OrderStatus.AWAITING_ACCEPTANCE,OrderStatus.REJECTED).contains(target))
      throw error(HttpStatus.FORBIDDEN,"Đầu bếp không có quyền thực hiện trạng thái này.");
    // Serialize all accept commands of one chef, including changes to their slots.
    // This lock must be acquired BEFORE locking orders to keep lock order consistent.
    var owner=jdbc.query("""
        SELECT u.id FROM users u JOIN chef_profiles cp ON cp.user_id=u.id
        WHERE u.id=? AND u.is_active=true AND cp.is_active=true AND cp.is_verified=true
        FOR UPDATE OF u
        """,(rs,n)->rs.getLong(1),chef);
    if (owner.isEmpty()) throw error(HttpStatus.FORBIDDEN,"Tài khoản đầu bếp chưa được duyệt hoặc bị vô hiệu hóa.");
    var rows=jdbc.query(SELECT+" AND o.id=? FOR UPDATE OF o",mapper,chef,id);
    if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Không tìm thấy đơn đặt nấu.");
    OrderRow order=rows.get(0);
    if (!order.status().equals(expected.name()) || order.version()!=request.version())
      throw error(HttpStatus.CONFLICT,"Đơn đã thay đổi. Hãy tải lại trước khi thao tác.");
    // Lock escrow after the order, consistent with the Payment/Refund transaction lock order.
    var escrows=jdbc.query("SELECT status FROM order_escrows WHERE order_id=? FOR UPDATE",
        (rs,n)->rs.getString(1),id);
    if (escrows.isEmpty() || !"HELD".equals(escrows.get(0)))
      throw error(HttpStatus.CONFLICT,"Đơn chưa có khoản thanh toán hợp lệ được tạm giữ.");
    if (target == OrderStatus.CONFIRMED) accept(chef,order);
    if (target == OrderStatus.REJECTED) {
      String reason=request.reason() == null ? "" : request.reason().trim();
      if (reason.length() < 10 || reason.length() > 1000)
        throw error(HttpStatus.BAD_REQUEST,"Vui lòng nhập lý do từ chối (10–1000 ký tự).");
      Boolean result=jdbc.queryForObject("select gocook_chef_reject_order(?,?,?)",Boolean.class,id,chef,reason);
      if (!Boolean.TRUE.equals(result)) throw error(HttpStatus.CONFLICT,"Đơn không còn ở trạng thái chờ nhận.");
      return get(chef,id);
    }
    if (target != OrderStatus.CONFIRMED) {
      Integer active=jdbc.queryForObject("""
          SELECT count(*) FROM chef_bookings WHERE order_id=? AND chef_id=? AND booking_status='ACTIVE'
          """,Integer.class,id,chef);
      if (active == null || active == 0)
        throw error(HttpStatus.CONFLICT,"Lịch đã nhận không còn hiệu lực. Hãy liên hệ hỗ trợ.");
    }
    int changed=jdbc.update("""
        UPDATE orders SET status=?,chef_completed_at=CASE WHEN ?='AWAITING_ACCEPTANCE'
        THEN now() ELSE chef_completed_at END,
        version=version+1,updated_at=(extract(epoch from now())*1000)::bigint
        WHERE id=? AND chef_id=? AND version=? AND status=? AND is_active=true
        """,target.name(),target.name(),id,chef,request.version(),expected.name());
    if (changed!=1) throw error(HttpStatus.CONFLICT,"Đơn đã được cập nhật bởi phiên khác.");
    jdbc.update("""
        INSERT INTO order_status_history(order_id,actor_user_id,previous_status,new_status,note)
        VALUES (?,?,?,?,?)
        """,id,chef,expected.name(),target.name(),"Chef cập nhật tiến độ");
    // Notification trigger (SQL 18) records the customer alert in this same transaction.
    return get(chef,id);
  }

  private void accept(long chef, OrderRow order) {
    LocalDateTime start=order.scheduledAt();
    int duration=order.durationMinutes();
    if (duration < 30 || duration > 1440 || !start.isAfter(LocalDateTime.now(ZONE))
        || !start.toLocalDate().equals(start.plusMinutes(duration).toLocalDate()))
      throw error(HttpStatus.CONFLICT,"Ngày/giờ hoặc thời lượng phục vụ không hợp lệ.");
    LocalDateTime end=start.plusMinutes(duration);
    Integer open=jdbc.queryForObject("""
        SELECT count(*) FROM seller_schedules s WHERE s.seller_id=? AND s.is_active=true
        AND s.available=true AND s.service_date=? AND s.start_time<=? AND s.end_time>=?
        """,Integer.class,chef,java.sql.Date.valueOf(start.toLocalDate()),
        java.sql.Time.valueOf(start.toLocalTime()),java.sql.Time.valueOf(end.toLocalTime()));
    if (open == null || open == 0)
      throw error(HttpStatus.CONFLICT,"Bạn chưa mở khung giờ bao phủ toàn bộ lịch phục vụ.");
    Integer collisions=jdbc.queryForObject("""
        SELECT count(*) FROM chef_bookings WHERE chef_id=? AND order_id<>?
        AND booking_status='ACTIVE' AND starts_at<? AND ends_at>?
        """,Integer.class,chef,order.id(),Timestamp.valueOf(end),Timestamp.valueOf(start));
    if (collisions!=null && collisions>0)
      throw error(HttpStatus.CONFLICT,"Đã có đơn được nhận trùng lịch phục vụ.");
    try {
      int updated=jdbc.update("""
          UPDATE chef_bookings SET booking_status='ACTIVE',starts_at=?,ends_at=?,updated_at=now()
          WHERE order_id=? AND chef_id=?
          """,Timestamp.valueOf(start),Timestamp.valueOf(end),order.id(),chef);
      if (updated==0) jdbc.update("""
          INSERT INTO chef_bookings(order_id,chef_id,starts_at,ends_at,booking_status)
          VALUES (?,?,?,?,'ACTIVE')
          """,order.id(),chef,Timestamp.valueOf(start),Timestamp.valueOf(end));
    } catch (DataIntegrityViolationException ex) {
      throw error(HttpStatus.CONFLICT,"Lịch vừa bị trùng với một đơn khác. Vui lòng tải lại.");
    }
  }

  private static OrderStatus parse(String raw) {
    if (raw==null) throw error(HttpStatus.BAD_REQUEST,"Thiếu trạng thái.");
    try { return OrderStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
    catch (IllegalArgumentException ex) { throw error(HttpStatus.BAD_REQUEST,"Trạng thái không hợp lệ."); }
  }
  private static ResponseStatusException error(HttpStatus status,String message) {
    return new ResponseStatusException(status,message);
  }
}
