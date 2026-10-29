package com.james.LMS.workflow;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** One private thread per actual order. Never changes settlement, escrow or order status. */
@RestController
@RequestMapping("/api/workflow/orders")
@PreAuthorize("isAuthenticated()")
public class OrderChatController {
  private final NamedParameterJdbcTemplate db;
  private final NotificationService notifications;
  public OrderChatController(NamedParameterJdbcTemplate db, NotificationService notifications) {
    this.db=db; this.notifications=notifications;
  }
  public record ChatInput(@NotBlank @Size(max=2000) String body) {}
  private MapSqlParameterSource args(Object... pairs) {
    var p=new MapSqlParameterSource();
    for(int i=0;i<pairs.length;i+=2) p.addValue((String)pairs[i],pairs[i+1]);
    return p;
  }
  private Map<String,Object> owner(long id, long userId) {
    var found=db.queryForList("""
      SELECT o.id,o.order_code AS "orderCode",o.customer_id AS "customerId",o.chef_id AS "chefId"
      FROM orders o WHERE o.id=:id AND (o.customer_id=:actor OR o.chef_id=:actor)
      """,args("id",id,"actor",userId));
    if(found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn của bạn.");
    var order=found.get(0);
    if(order.get("customerId")==null || order.get("chefId")==null)
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn chưa được gán đầu bếp.");
    return order;
  }
  @GetMapping("/{id}/messages")
  public List<Map<String,Object>> messages(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long id) {
    var order=owner(id,actor.getId());
    return db.queryForList("""
      SELECT m.id,m.sender_id AS "senderId",m.recipient_id AS "recipientId",u.username AS "senderName",
        m.body,m.created_at AS "createdAt"
      FROM order_messages m JOIN users u ON u.id=m.sender_id
      WHERE m.order_id=:order ORDER BY m.id DESC LIMIT 100
      """,args("order",order.get("id")));
  }
  @PostMapping("/{id}/messages")
  @Transactional
  public Map<String,String> send(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long id,
      @Valid @RequestBody ChatInput input) {
    var order=owner(id,actor.getId());
    long customer=((Number)order.get("customerId")).longValue();
    long chef=((Number)order.get("chefId")).longValue();
    if(customer==chef) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đơn không có hai bên tham gia.");
    long recipient=actor.getId()==customer?chef:customer;
    Long messageId=db.queryForObject("""
      INSERT INTO order_messages(order_id,sender_id,recipient_id,body)
      VALUES (:id,:sender,:recipient,:body) RETURNING id
      """,args("id",id,"sender",actor.getId(),"recipient",recipient,"body",input.body().trim()),Long.class);
    notifications.sendToUser(recipient,"order:message:"+messageId,"BOOKING","Có tin nhắn về đơn "+order.get("orderCode"),
        "Khách hoặc đầu bếp đã gửi tin nhắn về lịch nấu.","/chat?orderId="+id);
    return Map.of("status","SENT");
  }
}
