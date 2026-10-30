package com.james.LMS.workflow;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import com.james.LMS.notification.NotificationService;
import com.james.LMS.publiccontent.ServiceGeoArea;
import com.james.LMS.publiccontent.VerifiedLocationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Database-backed request -> matched chef -> offer -> booking -> payment workflow.
 * Do not treat a request or an offer as paid. Existing /api/payments/checkout/order is the payment source of truth.
 */
@RestController
public class ServiceWorkflowController {
  private final NamedParameterJdbcTemplate db;
  private final NotificationService notifications;
  private final VerifiedLocationService verifiedLocations;
  public ServiceWorkflowController(NamedParameterJdbcTemplate db, NotificationService notifications, VerifiedLocationService verifiedLocations) {
    this.db = db; this.notifications = notifications; this.verifiedLocations = verifiedLocations;
  }

  public record OfferInput(@NotNull @DecimalMin("1") @DecimalMax("1000000000") BigDecimal price,
      @NotBlank @Size(max=3000) String dishes, @Size(max=2000) String note) {}
  public record MatchInput(@NotNull @Positive Long chefId) {}
  public record ChatInput(@NotNull @Positive Long recipientId, @NotBlank @Size(max=2000) String body) {}
  public record ChefProfileInput(@NotBlank @Size(max=255) String displayName,
      @Size(max=2000) String bio, @Size(max=500) String serviceArea, @Size(max=30) String phone) {}

  private MapSqlParameterSource args(Object... pairs) {
    var p = new MapSqlParameterSource();
    for (int i=0;i<pairs.length;i+=2) p.addValue((String)pairs[i], pairs[i+1]);
    return p;
  }
  private Map<String,Object> request(long id) {
    var rows = db.queryForList("""
      SELECT r.id,r.request_code AS "requestCode",r.customer_id AS "customerId",u.username AS "customerName",
        r.preferred_chef_id AS "preferredChefId",r.preferred_menu_id AS "preferredMenuId",
        r.accepted_order_id AS "acceptedOrderId",
        (SELECT o.order_code FROM orders o WHERE o.id=r.accepted_order_id) AS "acceptedOrderCode",
        (SELECT o.status FROM orders o WHERE o.id=r.accepted_order_id) AS "acceptedOrderStatus",
        (SELECT e.status FROM order_escrows e WHERE e.order_id=r.accepted_order_id) AS "acceptedEscrowStatus",
        r.desired_dishes AS "desiredDishes",r.requested_area AS "serviceArea",r.cuisine,
        r.adults,r.children,r.avoid_ingredients AS "avoid",r.spice_level AS "spice",
        r.meal_date AS "mealDate",r.meal_time AS "mealTime",r.kitchen_address AS "address",
        r.equipment_available AS "equipment",r.budget_min AS "budgetMin",r.budget_max AS "budgetMax",
        r.status,r.created_at AS "createdAt"
      FROM public_cooking_requests r JOIN users u ON u.id=r.customer_id WHERE r.id=:id
    """, args("id",id));
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu.");
    return rows.get(0);
  }
  private LocalDate day(Object value) {
    if (value instanceof LocalDate date) return date;
    if (value instanceof java.sql.Date date) return date.toLocalDate();
    return LocalDate.parse(String.valueOf(value));
  }
  private boolean admin(SecurityUserDetails actor) {
    return actor.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN"));
  }
  private boolean chef(SecurityUserDetails actor) {
    return actor.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_INSTRUCTOR"));
  }
  private void requireParticipant(SecurityUserDetails actor, long id) {
    var r=request(id);
    if (admin(actor) || ((Number)r.get("customerId")).longValue()==actor.getId()) return;
    if (chef(actor) && !db.queryForList("SELECT 1 FROM cooking_request_matches WHERE request_id=:id AND chef_id=:chef",
        args("id",id,"chef",actor.getId())).isEmpty()) return;
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không tham gia yêu cầu này.");
  }

  @GetMapping("/api/customer/cooking-requests")
  @PreAuthorize("isAuthenticated()")
  public List<Map<String,Object>> customerRequests(@AuthenticationPrincipal SecurityUserDetails actor) {
    return db.queryForList("""
      SELECT r.id, r.customer_id AS "customerId",r.request_code AS "requestCode",r.desired_dishes AS "desiredDishes",r.meal_date AS "mealDate",
        r.status,r.budget_max AS "budgetMax",r.accepted_order_id AS "acceptedOrderId",
        (SELECT o.order_code FROM orders o WHERE o.id=r.accepted_order_id) AS "acceptedOrderCode",
        (SELECT o.status FROM orders o WHERE o.id=r.accepted_order_id) AS "acceptedOrderStatus",
        (SELECT e.status FROM order_escrows e WHERE e.order_id=r.accepted_order_id) AS "acceptedEscrowStatus",
        (SELECT count(*) FROM cooking_request_offers o WHERE o.request_id=r.id AND o.status='SENT') AS "offerCount"
      FROM public_cooking_requests r WHERE r.customer_id=:customer ORDER BY r.created_at DESC LIMIT 100
    """, args("customer",actor.getId()));
  }

  @GetMapping("/api/customer/cooking-requests/{id}")
  @PreAuthorize("isAuthenticated()")
  public Map<String,Object> customerDetail(@AuthenticationPrincipal SecurityUserDetails actor, @PathVariable long id) {
    var r=request(id);
    if (((Number)r.get("customerId")).longValue()!=actor.getId() && !admin(actor))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return detail(id);
  }
  private Map<String,Object> detail(long id) {
    var result=new java.util.LinkedHashMap<String,Object>(request(id));
    result.put("matches",db.queryForList("""
      SELECT m.chef_id AS "chefId",cp.display_name AS "chefName",split_part(cp.service_area,'|',1) AS "serviceArea",
        cp.rating,m.match_score AS "matchScore",m.match_reason AS "matchReason"
      FROM cooking_request_matches m JOIN chef_profiles cp ON cp.user_id=m.chef_id
      WHERE m.request_id=:id ORDER BY m.match_score DESC,cp.rating DESC
    """,args("id",id)));
    result.put("offers",db.queryForList("""
      SELECT o.id,o.chef_id AS "chefId",cp.display_name AS "chefName",o.proposed_price AS "price",
        o.dishes,o.note,o.status,o.updated_at AS "updatedAt"
      FROM cooking_request_offers o JOIN chef_profiles cp ON cp.user_id=o.chef_id
      WHERE o.request_id=:id ORDER BY o.updated_at DESC
    """,args("id",id)));
    return result;
  }

  @GetMapping("/api/chef/cooking-requests")
  @PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
  public List<Map<String,Object>> chefRequests(@AuthenticationPrincipal SecurityUserDetails actor) {
    return db.queryForList("""
      SELECT r.id,r.request_code AS "requestCode",r.customer_id AS "customerId",r.desired_dishes AS "desiredDishes",r.cuisine,
        r.meal_date AS "mealDate",r.meal_time AS "mealTime",r.kitchen_address AS "address",
        r.requested_area AS "serviceArea",u.username AS "customerName",
        r.adults,r.children,r.budget_min AS "budgetMin",r.budget_max AS "budgetMax",r.status,
        o.proposed_price AS "myPrice",o.status AS "myOfferStatus",
        CASE WHEN o.status='ACCEPTED' THEN r.accepted_order_id END AS "myOrderId",
        CASE WHEN o.status='ACCEPTED' THEN (SELECT od.order_code FROM orders od WHERE od.id=r.accepted_order_id) END AS "myOrderCode",
        CASE WHEN o.status='ACCEPTED' THEN (SELECT e.status FROM order_escrows e WHERE e.order_id=r.accepted_order_id) END AS "myEscrowStatus"
      FROM cooking_request_matches m JOIN public_cooking_requests r ON r.id=m.request_id
      JOIN users u ON u.id=r.customer_id
      LEFT JOIN cooking_request_offers o ON o.request_id=r.id AND o.chef_id=m.chef_id
      WHERE m.chef_id=:chef ORDER BY r.created_at DESC LIMIT 100
    """,args("chef",actor.getId()));
  }

  @PostMapping("/api/chef/cooking-requests/{id}/offers")
  @PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
  @Transactional
  public Map<String,Object> quote(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long id,
      @Valid @RequestBody OfferInput body) {
    var r=requestLocked(id);
    if ("CLOSED".equals(r.get("status")) || day(r.get("mealDate")).isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Yêu cầu đã đóng hoặc đã quá ngày phục vụ.");
    requireParticipant(actor,id);
    if (db.queryForList("""
        SELECT 1 FROM chef_profiles cp JOIN users u ON u.id=cp.user_id
        WHERE cp.user_id=:id AND cp.is_active=true AND cp.is_verified=true
          AND cp.verification_status='APPROVED' AND u.is_active=true
        """,args("id",actor.getId())).isEmpty())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Hồ sơ đầu bếp chưa được phê duyệt.");
    if (body.price().stripTrailingZeros().scale()>0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Giá báo phải là số VND nguyên, không có phần lẻ.");
    var p=args("id",id,"chef",actor.getId(),"price",body.price(),"dishes",body.dishes().trim(),"note",body.note());
    db.update("""
      INSERT INTO cooking_request_offers(request_id,chef_id,proposed_price,dishes,note)
      VALUES (:id,:chef,:price,:dishes,:note)
      ON CONFLICT (request_id,chef_id) DO UPDATE SET proposed_price=EXCLUDED.proposed_price,
        dishes=EXCLUDED.dishes,note=EXCLUDED.note,updated_at=now()
      WHERE cooking_request_offers.status='SENT'
    """,p);
    db.update("UPDATE public_cooking_requests SET status='REVIEWING',updated_at=now() WHERE id=:id AND status='PENDING'",args("id",id));
    notifications.sendToUser(((Number)r.get("customerId")).longValue(),
      "offer:"+id+":"+actor.getId()+":"+UUID.randomUUID(),"REQUEST","Đầu bếp đã gửi báo giá",
      "Yêu cầu của bạn đã có báo giá. Hãy xem và thanh toán sau khi chọn.",
      "/request-detail?id="+id);
    return Map.of("status","SENT");
  }

  @PostMapping("/api/customer/cooking-requests/{id}/offers/{offerId}/accept")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public Map<String,Object> accept(@AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable long id,@PathVariable long offerId) {
    var r=requestLocked(id);
    if (((Number)r.get("customerId")).longValue()!=actor.getId())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (r.get("acceptedOrderId")!=null) {
      var existing=db.queryForMap("SELECT id,order_code AS \"orderCode\" FROM orders WHERE id=:id",args("id",r.get("acceptedOrderId")));
      return Map.of("orderId",existing.get("id"),"orderCode",existing.get("orderCode"));
    }
    if ("CLOSED".equals(r.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Yêu cầu đã đóng.");
    var offers=db.queryForList("""
      SELECT o.chef_id,o.proposed_price,o.dishes,o.status,cp.display_name AS chef_name
      FROM cooking_request_offers o JOIN chef_profiles cp ON cp.user_id=o.chef_id
      JOIN users u ON u.id=o.chef_id
      WHERE o.id=:offer AND o.request_id=:request AND cp.is_active=true AND cp.is_verified=true
        AND cp.verification_status='APPROVED' AND u.is_active=true FOR UPDATE OF o
    """,args("offer",offerId,"request",id));
    if (offers.isEmpty() || !"SENT".equals(offers.get(0).get("status")))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Báo giá không còn hợp lệ.");
    var o=offers.get(0);
    LocalTime time="Bữa trưa".equals(r.get("mealTime"))?LocalTime.of(11,30):LocalTime.of(18,30);
    LocalDateTime starts=day(r.get("mealDate")).atTime(time);
    if (!starts.isAfter(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Giờ phục vụ đã qua.");
    String code="GC-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
    var user=db.queryForMap("SELECT username,email,phone FROM users WHERE id=:id",args("id",actor.getId()));
    var orderArgs=args("code",code,"customer",actor.getId(),"name",user.get("username"),
      "email",user.get("email"),"phone",user.get("phone"),"chef",o.get("chef_id"),"chefName",o.get("chef_name"),
      "dishes",((String)o.get("dishes")).substring(0,Math.min(255,((String)o.get("dishes")).length())),
      "address",r.get("address"),"guests",((Number)r.get("adults")).intValue()+((Number)r.get("children")).intValue(),
      "starts",starts,"total",o.get("proposed_price"),"note",r.get("avoid"),
      "serviceType",r.get("preferredMenuId") == null ? "BUDGET" : "MENU");
    Long orderId=db.queryForObject("""
      INSERT INTO orders(order_code,customer_name,customer_email,customer_id,customer_phone,product_name,
        chef_name,chef_id,service_address,guest_count,scheduled_at,total_amount,status,customer_note,service_type,service_duration_minutes)
      VALUES (:code,:name,:email,:customer,:phone,:dishes,:chefName,:chef,:address,:guests,:starts,
        :total,'PENDING',:note,:serviceType,180) RETURNING id
    """,orderArgs,Long.class);
    // Snapshot the accepted line for history, invoices and admin order detail.
    db.update("""
      INSERT INTO order_items(order_id,product_id,product_name_snapshot,unit_price_snapshot,
        guest_count,quantity,line_total)
      VALUES (:order,:product,:name,:unit,:guests,1,:total)
    """,args("order",orderId,"product",r.get("preferredMenuId"),"name",orderArgs.getValue("dishes"),
        "total",o.get("proposed_price"),"unit",((BigDecimal)o.get("proposed_price"))
            .divide(BigDecimal.valueOf(((Number)orderArgs.getValue("guests")).longValue()),2,java.math.RoundingMode.HALF_UP),
        "guests",orderArgs.getValue("guests")));
    try {
      db.update("""
        INSERT INTO chef_bookings(order_id,chef_id,starts_at,ends_at,booking_status)
        VALUES (:order,:chef,:starts,:ends,'ACTIVE')
      """,args("order",orderId,"chef",o.get("chef_id"),"starts",starts,"ends",starts.plusHours(3)));
    } catch(DataIntegrityViolationException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Đầu bếp đã có lịch trong khung giờ này.",exception);
    }
    db.update("UPDATE public_cooking_requests SET status='CLOSED',accepted_order_id=:order,updated_at=now() WHERE id=:id",
      args("id",id,"order",orderId));
    db.update("UPDATE cooking_request_offers SET status=CASE WHEN id=:offer THEN 'ACCEPTED' ELSE 'REJECTED' END,updated_at=now() WHERE request_id=:id",
      args("id",id,"offer",offerId));
    notifications.sendToUser(((Number)o.get("chef_id")).longValue(),
      "booking:awaiting-payment:"+orderId,"BOOKING","Khách đã chọn báo giá",
      "Đơn "+code+" đang chờ khách thanh toán VNPay. Chưa nhận nấu lúc này.",
      "/seller/order-detail?id="+orderId);
    notifications.sendToAdmins("booking:created:"+orderId,"BOOKING","Có đơn đặt nấu mới",
      "Đơn "+code+" được tạo từ báo giá, đang chờ VNPay.",
      "/admin/order-detail?id="+orderId);
    return Map.of("orderId",orderId,"orderCode",code);
  }
  private Map<String,Object> requestLocked(long id) {
    var rows=db.queryForList("SELECT r.*,r.customer_id AS \"customerId\",r.accepted_order_id AS \"acceptedOrderId\",r.meal_date AS \"mealDate\",r.meal_time AS \"mealTime\",r.kitchen_address AS address,r.avoid_ingredients AS avoid,r.preferred_menu_id AS \"preferredMenuId\" FROM public_cooking_requests r WHERE id=:id FOR UPDATE",args("id",id));
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu.");
    return rows.get(0);
  }

  @PostMapping("/api/admin/cooking-requests/{id}/match")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  @Transactional
  public Map<String,Object> assign(@PathVariable long id,@Valid @RequestBody MatchInput input) {
    var r=requestLocked(id);
    if ("CLOSED".equals(r.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Yêu cầu đã đóng.");
    String requestArea = String.valueOf(r.get("requested_area"));
    ServiceGeoArea.customer(requestArea); // reject old province-only requests, never guess distance
    var eligible=db.queryForList("""
      SELECT cp.service_area AS "chefArea" FROM chef_profiles cp JOIN users u ON u.id=cp.user_id
      WHERE cp.user_id=:chef AND cp.is_active=true AND cp.is_verified=true
        AND cp.verification_status='APPROVED' AND u.is_active=true
        AND NOT EXISTS (SELECT 1 FROM chef_bookings b WHERE b.chef_id=cp.user_id
             AND b.booking_status='ACTIVE' AND b.starts_at < :endTime AND b.ends_at > :startTime)
    """,args("chef",input.chefId(),
          "startTime",day(r.get("mealDate")).atTime("Bữa trưa".equals(r.get("mealTime")) ? LocalTime.of(11,30) : LocalTime.of(18,30)),
          "endTime",day(r.get("mealDate")).atTime("Bữa trưa".equals(r.get("mealTime")) ? LocalTime.of(14,30) : LocalTime.of(21,30))));
    if (eligible.isEmpty() || !ServiceGeoArea.matches((String)eligible.get(0).get("chefArea"),requestArea))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Đầu bếp chưa được duyệt, ngoài bán kính phục vụ hoặc bận lịch.");
    db.update("""
      INSERT INTO cooking_request_matches(request_id,chef_id,match_score,match_reason)
      VALUES (:id,:chef,100,'Admin đã chỉ định') ON CONFLICT (request_id,chef_id) DO NOTHING
    """,args("id",id,"chef",input.chefId()));
    db.update("UPDATE public_cooking_requests SET status='REVIEWING',updated_at=now() WHERE id=:id",args("id",id));
    notifications.sendToUser(input.chefId(), "request:matched:"+id, "REQUEST",
      "Bạn được ghép với yêu cầu mới", "Yêu cầu "+r.get("request_code")+" đã được phân công. Xem chi tiết để gửi báo giá.",
      "/seller/requests");
    return Map.of("status","MATCHED");
  }

  @GetMapping("/api/workflow/requests/{id}/messages")
  @PreAuthorize("isAuthenticated()")
  public List<Map<String,Object>> messages(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long id,
      @RequestParam long peerId) {
    requireParticipant(actor,id);
    if (actor.getId()!=peerId && !admin(actor)) {
      var r=request(id);
      if (!(((Number)r.get("customerId")).longValue()==peerId && chef(actor)) &&
          db.queryForList("SELECT 1 FROM cooking_request_matches WHERE request_id=:id AND chef_id=:peer",
             args("id",id,"peer",peerId)).isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    return db.queryForList("""
      SELECT m.id,m.sender_id AS "senderId",m.recipient_id AS "recipientId",m.body,
        m.created_at AS "createdAt",u.username AS "senderName"
      FROM cooking_request_messages m JOIN users u ON u.id=m.sender_id
      WHERE m.request_id=:id AND ((m.sender_id=:actor AND m.recipient_id=:peer)
        OR (m.sender_id=:peer AND m.recipient_id=:actor)) ORDER BY m.id DESC LIMIT 100
    """,args("id",id,"actor",actor.getId(),"peer",peerId));
  }
  @PostMapping("/api/workflow/requests/{id}/messages")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public Map<String,Object> send(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long id,
      @Valid @RequestBody ChatInput message) {
    requireParticipant(actor,id);
    var r=request(id);
    long customerId=((Number)r.get("customerId")).longValue();
    long receiver=message.recipientId();
    if (actor.getId()==receiver || !(
      (actor.getId()==customerId && !db.queryForList("SELECT 1 FROM cooking_request_matches WHERE request_id=:id AND chef_id=:chef",args("id",id,"chef",receiver)).isEmpty()) ||
      (receiver==customerId && chef(actor) && !db.queryForList("SELECT 1 FROM cooking_request_matches WHERE request_id=:id AND chef_id=:chef",args("id",id,"chef",actor.getId())).isEmpty())
    )) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ khách và đầu bếp được ghép mới có thể nhắn tin.");
    Long messageId=db.queryForObject("""
        INSERT INTO cooking_request_messages(request_id,sender_id,recipient_id,body)
        VALUES (:id,:sender,:receiver,:body) RETURNING id
        """,args("id",id,"sender",actor.getId(),"receiver",receiver,"body",message.body().trim()),Long.class);
    notifications.sendToUser(receiver, "request:message:"+messageId, "REQUEST", "Bạn có tin nhắn mới",
        "Có phản hồi mới về yêu cầu "+r.get("requestCode")+". Mở cuộc trò chuyện để xem.",
        "/chat?requestId="+id+"&peerId="+actor.getId());
    return Map.of("status","SENT");
  }

  @GetMapping("/api/chef/profile")
  @PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
  public Map<String,Object> profile(@AuthenticationPrincipal SecurityUserDetails actor) {
    var rows=db.queryForList("""
      SELECT display_name AS "displayName",bio,service_area AS "serviceArea",phone
      FROM chef_profiles WHERE user_id=:id
    """,args("id",actor.getId()));
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ đầu bếp.");
    return rows.get(0);
  }
  private String validatedChefArea(String area) {
    return verifiedLocations.confirmChef(area); // verified province + ward + coordinate proof
  }
  @PutMapping("/api/chef/profile")
  @PreAuthorize("hasAuthority('ROLE_INSTRUCTOR')")
  public Map<String,String> updateProfile(@AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestBody ChefProfileInput input) {
    int changed=db.update("""
      UPDATE chef_profiles SET display_name=:name,bio=:bio,service_area=:area,phone=:phone,
        updated_at=(EXTRACT(EPOCH FROM NOW())*1000)::bigint WHERE user_id=:id
    """,args("id",actor.getId(),"name",input.displayName().trim(),"bio",input.bio(),
      "area",validatedChefArea(input.serviceArea()),"phone",input.phone()));
    if (changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ đầu bếp.");
    return Map.of("status","UPDATED");
  }
}
