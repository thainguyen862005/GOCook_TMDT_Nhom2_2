package com.james.LMS.refund;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Cancels eligible orders and opens a refund request. Never reports a simulated transfer. */
@RestController
@RequiredArgsConstructor
public class OrderCancellationController {
  private final NamedParameterJdbcTemplate db;
  private final RefundService refunds;
  private final NotificationService notifications;
  public record CancelInput(@NotBlank @Size(min=10,max=500) String reason) {}

  @PostMapping("/api/customer/orders/{id}/cancel")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public Map<String,Object> customer(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long id,
      @Valid @RequestBody CancelInput input) {
    return cancel(id,actor.getId(),false,input.reason(),CancelledBy.CUSTOMER);
  }

  @PostMapping("/api/admin/orders/{id}/cancel")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  @Transactional
  public Map<String,Object> admin(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long id,
      @Valid @RequestBody CancelInput input) {
    return cancel(id,actor.getId(),true,input.reason(),CancelledBy.ADMIN);
  }

  private Map<String,Object> cancel(long id,long actorId,boolean admin,String reason,CancelledBy by) {
    var args=new MapSqlParameterSource("id",id).addValue("actor",actorId);
    var rows=db.queryForList("""
      SELECT id,customer_id,chef_id,status,scheduled_at FROM orders
      WHERE id=:id AND is_active=true FOR UPDATE
      """,args);
    if(rows.isEmpty() || (!admin && ((Number)rows.get(0).get("customer_id")).longValue()!=actorId))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn của bạn.");
    Map<String,Object> order=rows.get(0);
    String state=(String)order.get("status");
    if(!"PENDING".equals(state) && !"CONFIRMED".equals(state))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn đang phục vụ hoặc đã hoàn tất; vui lòng liên hệ hỗ trợ.");
    var escrows=db.queryForList("SELECT status FROM order_escrows WHERE order_id=:id FOR UPDATE",args);
    String escrow=escrows.isEmpty()?null:(String)escrows.get(0).get("status");
    if(escrow!=null && !"HELD".equals(escrow))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Tiền đang xử lý. Vui lòng đợi Admin đối soát.");
    if(escrow==null && !db.queryForList("""
        SELECT 1 FROM payment_transactions WHERE order_id=:id AND status IN ('PENDING','SUCCESS') LIMIT 1
        """,args).isEmpty())
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Giao dịch VNPay chưa đối soát; không hủy trước khi xác nhận tiền.");
    String note=reason.trim();
    db.update("""
        UPDATE orders SET status='CANCELLED',version=version+1,
          updated_at=(EXTRACT(EPOCH FROM now())*1000)::bigint WHERE id=:id
        """,args);
    db.update("""
        UPDATE chef_bookings SET booking_status='RELEASED', updated_at=now() WHERE order_id=:id AND booking_status='ACTIVE'
        """,args);
    db.update("""
        INSERT INTO order_status_history(order_id,actor_user_id,previous_status,new_status,note)
        VALUES (:id,:actor,:from,'CANCELLED',:reason)
        """,args.addValue("from",state).addValue("reason",note));
    Object rawSchedule=order.get("scheduled_at");
    LocalDateTime scheduledAt=rawSchedule instanceof java.sql.Timestamp time
        ? time.toLocalDateTime() : (LocalDateTime)rawSchedule;
    var refund=refunds.queueRefund(new CancellationContext(id,by,OffsetDateTime.now(),
        scheduledAt,note));
    long customerId=((Number)order.get("customer_id")).longValue();
    notifications.sendToUser(customerId,"order:cancelled:"+id,"BOOKING", "Đơn đã được hủy",
      refund.isPresent()?"Đã tạo yêu cầu hoàn tiền, đang chờ cổng thanh toán đối soát.":"Đơn chưa thu tiền; không phát sinh hoàn tiền.",
      "/order-detail?id="+id);
    if(order.get("chef_id") instanceof Number chefId)
      notifications.sendToUser(chefId.longValue(),"order:cancelled-chef:"+id,"BOOKING",
        "Đơn đặt nấu đã hủy","Lịch phục vụ đã được giải phóng.","/seller/order-detail?id="+id);
    return Map.of("status","CANCELLED","refundStatus",refund.isPresent()?refund.get().status().name():"NOT_REQUIRED");
  }
}
