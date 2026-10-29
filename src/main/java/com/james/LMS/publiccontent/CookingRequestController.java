package com.james.LMS.publiccontent;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.notification.NotificationService;
import org.springframework.transaction.support.TransactionTemplate;
import com.james.LMS.media.ImageFileValidator;
import com.james.LMS.service.CloudinaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Intake only. Booking, quotation and payment are owned by their separate domains. */
@RestController
public class CookingRequestController {
  private final NamedParameterJdbcTemplate jdbc;
  private final CloudinaryService cloudinary;
  private final NotificationService notifications;
  private final TransactionTemplate transactions;
  public CookingRequestController(NamedParameterJdbcTemplate jdbc, CloudinaryService cloudinary,
                                  NotificationService notifications, TransactionTemplate transactions) {
    this.jdbc = jdbc; this.cloudinary = cloudinary; this.notifications = notifications;
    this.transactions = transactions;
  }

  public record RequestInput(
      @NotBlank @Size(max=3000) String desiredDishes,
      @NotBlank @Size(max=100) String cuisine,
      @Min(1) @Max(100) int adults,
      @Min(0) @Max(100) int children,
      @Size(max=2000) String avoid,
      @NotBlank @Size(max=50) String spice,
      @NotNull @FutureOrPresent LocalDate mealDate,
      @NotBlank @Size(max=30) String mealTime,
      @NotBlank @Size(min=8, max=500) String address,
      boolean equipment,
      @NotNull @DecimalMin("1") BigDecimal budgetMin,
      @NotNull @DecimalMin("1") BigDecimal budgetMax,
      @Positive Long preferredChefId, @Positive Long preferredMenuId,
      @Size(max=100) String serviceArea) {}

  static Long resolveChefId(Long preferredChefId, Map<String,Object> chosenMenu) {
    // Menu ownership is not Chef assignment. A customer can choose another Chef or request matching.
    return preferredChefId;
  }

  @PostMapping(value="/api/customer/cooking-requests", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String,String> create(@AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestPart("request") RequestInput data,
      @RequestPart(value="referenceImage",required=false) MultipartFile image) {
    if (actor == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
    var verified = jdbc.queryForList("SELECT email_verified FROM users WHERE id=:id AND is_active=true",
        new MapSqlParameterSource("id",actor.getId()));
    if (verified.isEmpty() || !Boolean.TRUE.equals(verified.get(0).get("email_verified")))
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"Cần xác minh email.");
    Long recent = jdbc.queryForObject("""
        SELECT COUNT(*) FROM public_cooking_requests WHERE customer_id=:customer
        AND created_at > now()-interval '1 hour'
        """,new MapSqlParameterSource("customer",actor.getId()),Long.class);
    if (recent != null && recent >= 5)
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Vui lòng thử lại sau.");
    if (data.budgetMax().compareTo(data.budgetMin()) < 0 || data.budgetMax().compareTo(new BigDecimal("1000000000")) > 0)
      throw new IllegalArgumentException("Ngân sách không hợp lệ.");
    if (data.adults() + data.children() > 100) throw new IllegalArgumentException("Số khách vượt giới hạn.");
    Map<String,Object> chosenMenu = null;
    if (data.preferredMenuId() != null) {
      var found = jdbc.queryForList("""
          SELECT p.seller_id AS "chefId",p.unit_price AS "unitPrice",p.name,p.category,
            p.min_guests AS "minGuests",p.max_guests AS "maxGuests"
          FROM products p
          WHERE p.id=:id AND p.is_active=true AND p.status='PUBLISHED'
          """, new MapSqlParameterSource("id",data.preferredMenuId()));
      if (found.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Thực đơn không còn phục vụ.");
      chosenMenu=found.get(0);
      int count=data.adults()+data.children();
      if (count < ((Number)chosenMenu.get("minGuests")).intValue() || count > ((Number)chosenMenu.get("maxGuests")).intValue())
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Số người ngoài giới hạn thực đơn.");
      // Chef other than the menu creator must quote separately; the creator's fixed price
      // must NEVER be charged on behalf of an unrelated Chef.
    }
    // A general cooking request intentionally has no preferred chef. Avoid Long/long
    // ternary promotion, which would unbox null and throw NullPointerException.
    final Long targetChef = resolveChefId(data.preferredChefId(), chosenMenu);
    final String region = data.serviceArea() == null ? "" : data.serviceArea().trim();
    final String foodStyle = chosenMenu == null ? data.cuisine() : String.valueOf(chosenMenu.get("category"));
    final LocalTime mealHour = "Bữa trưa".equals(data.mealTime()) ? LocalTime.of(11,30) : LocalTime.of(18,30);
    final LocalDateTime begin = data.mealDate().atTime(mealHour);
    if (!begin.isAfter(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Ngày và giờ nấu phải ở tương lai.");
    if (targetChef!=null && jdbc.queryForList("""
        SELECT 1 FROM chef_profiles cp JOIN users u ON u.id=cp.user_id
        WHERE cp.user_id=:chef AND cp.is_active=true AND cp.is_verified=true
          AND cp.verification_status='APPROVED' AND u.is_active=true
          AND (:area='' OR COALESCE(cp.service_area,'') ILIKE '%' || :area || '%')
          AND NOT EXISTS (SELECT 1 FROM chef_bookings b WHERE b.chef_id=cp.user_id
            AND b.booking_status='ACTIVE' AND b.starts_at < :endTime AND b.ends_at > :startTime)
        """,new MapSqlParameterSource("chef",targetChef).addValue("area",region)
          .addValue("startTime",begin).addValue("endTime",begin.plusHours(3))).isEmpty())
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Đầu bếp chưa được duyệt, không phục vụ khu vực đã chọn hoặc bận lịch này.");
    byte[] imageBytes = image == null || image.isEmpty() ? null : ImageFileValidator.validate(image);
    CloudinaryService.MenuImage uploaded = null;
    // Upload first; delete on failed INSERT to avoid orphaned media.
    if (imageBytes != null) uploaded = cloudinary.uploadMenuImage(imageBytes);
    String code = "REQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
    final String uploadedUrl = uploaded == null ? null : uploaded.url();
    try {
      // TransactionTemplate makes commit errors observable here, allowing Cloudinary cleanup.
      transactions.executeWithoutResult(tx -> {
      jdbc.update("""
          INSERT INTO public_cooking_requests(request_code,customer_id,desired_dishes,cuisine,
            adults,children,avoid_ingredients,spice_level,meal_date,meal_time,
            kitchen_address,equipment_available,budget_min,budget_max,reference_image_url,status,preferred_chef_id,preferred_menu_id,requested_area)
          VALUES (:code,:customer,:dishes,:cuisine,:adults,:children,:avoid,:spice,
            :mealDate,:mealTime,:address,:equipment,:budgetMin,:budgetMax,:image,'PENDING',:chef,:menu,:area)
          """, new MapSqlParameterSource().addValue("code", code).addValue("customer", actor.getId())
          .addValue("dishes", data.desiredDishes().trim()).addValue("cuisine", data.cuisine().trim())
          .addValue("adults", data.adults()).addValue("children", data.children())
          .addValue("avoid", data.avoid()).addValue("spice", data.spice())
          .addValue("mealDate", data.mealDate()).addValue("mealTime", data.mealTime())
          .addValue("address", data.address().trim()).addValue("equipment", data.equipment())
          .addValue("budgetMin", data.budgetMin()).addValue("budgetMax", data.budgetMax())
          .addValue("image", uploadedUrl).addValue("chef",targetChef).addValue("menu",data.preferredMenuId()).addValue("area",region));
      Long requestId=jdbc.queryForObject("SELECT id FROM public_cooking_requests WHERE request_code=:code",
          new MapSqlParameterSource("code",code),Long.class);
      // Match only approved active chefs. Preference is exclusive; general requests receive
      // a small deterministic shortlist based on service area, cuisine/menu and rating.
      // Matching is area and schedule aware. A menu is a recipe reference, not a price promise
      // from another chef. Every matched chef must respond with their own quote.
      jdbc.update("""
          INSERT INTO cooking_request_matches(request_id,chef_id,match_score,match_reason)
          SELECT :request, candidates.chef_id, candidates.score, candidates.reason
          FROM (SELECT cp.user_id AS chef_id,
            (CASE WHEN :area<>'' THEN 35 ELSE 0 END
             + CASE WHEN EXISTS (SELECT 1 FROM products p WHERE p.seller_id=cp.user_id
                AND p.is_active=true AND p.status='PUBLISHED' AND p.category ILIKE :style) THEN 45 ELSE 0 END
             + LEAST(COALESCE(cp.rating,0)::int * 4,20)) AS score,
            CASE WHEN CAST(:preferred AS BIGINT) IS NOT NULL THEN 'Được khách lựa chọn'
                 ELSE 'Gợi ý theo khu vực, món ăn và lịch trống' END AS reason
          FROM chef_profiles cp JOIN users u ON u.id=cp.user_id
          WHERE cp.is_active=true AND cp.is_verified=true AND cp.verification_status='APPROVED'
            AND u.is_active=true
            AND (CAST(:preferred AS BIGINT) IS NULL OR cp.user_id=:preferred)
            AND (:area='' OR COALESCE(cp.service_area,'') ILIKE '%' || :area || '%')
            AND NOT EXISTS (SELECT 1 FROM chef_bookings b WHERE b.chef_id=cp.user_id
              AND b.booking_status='ACTIVE' AND b.starts_at < :endTime AND b.ends_at > :startTime)
            AND (CAST(:preferred AS BIGINT) IS NOT NULL OR EXISTS (
              SELECT 1 FROM products p WHERE p.seller_id=cp.user_id AND p.is_active=true
                AND p.status='PUBLISHED' AND p.category ILIKE :style))
          ORDER BY score DESC, cp.rating DESC, cp.user_id LIMIT 5) candidates
          ON CONFLICT (request_id,chef_id) DO NOTHING
          """,new MapSqlParameterSource("request",requestId).addValue("preferred",targetChef)
             .addValue("area",region).addValue("style","%"+foodStyle+"%")
             .addValue("startTime",begin).addValue("endTime",begin.plusHours(3)));
      // Do not fabricate an offer from a chef without their consent. All menu requests
      // (including one directed to the original author) await an explicit Chef quote.

      var matchedChefs = jdbc.queryForList(
          "SELECT chef_id FROM cooking_request_matches WHERE request_id=:id",
          new MapSqlParameterSource("id",requestId));
      for (var match : matchedChefs) {
        notifications.sendToUser(((Number)match.get("chef_id")).longValue(),
            "request:matched:"+requestId, "REQUEST", "Có yêu cầu nấu ăn phù hợp",
            "Yêu cầu " + code + " đã được ghép với bạn. Vào xem và gửi báo giá.", "/seller/requests");
      }
      notifications.sendToUser(actor.getId(), "request-created:"+code, "REQUEST", "Đã nhận yêu cầu nấu ăn",
          "Yêu cầu " + code + " đang chờ đầu bếp phản hồi.", "/request-detail?id="+requestId);
      notifications.sendToAdmins("request-new:"+code, "REQUEST", "Yêu cầu nấu ăn mới",
          "Khách hàng vừa gửi yêu cầu " + code + ".", "/admin/cooking-requests");
      });
    } catch (RuntimeException error) {
      if (uploaded != null) {
        try { cloudinary.deleteMenuImage(uploaded.publicId()); }
        catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
      }
      throw error;
    }
    Long savedId=jdbc.queryForObject("SELECT id FROM public_cooking_requests WHERE request_code=:code",
        new MapSqlParameterSource("code",code),Long.class);
    return Map.of("id",String.valueOf(savedId),"requestCode",code,"status","PENDING");
  }

  /** Administrator queue, deliberately separate from orders / payments. */
  @GetMapping("/api/admin/cooking-requests")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  public List<Map<String,Object>> listForAdmin(@RequestParam(defaultValue="0") int page) {
    if (page < 0 || page > 100000) throw new IllegalArgumentException("Trang không hợp lệ.");
    return jdbc.queryForList("""
        SELECT r.id,r.request_code AS "requestCode",r.customer_id AS "customerId",
          u.username AS "customerName",r.desired_dishes AS "desiredDishes",r.cuisine,
          r.adults,r.children,r.avoid_ingredients AS "avoid",r.spice_level AS "spice",
          r.meal_date AS "mealDate",r.meal_time AS "mealTime",
          r.kitchen_address AS "address",r.equipment_available AS "equipment",
          r.budget_min AS "budgetMin",r.budget_max AS "budgetMax",
          r.reference_image_url AS "referenceImageUrl",r.status,r.created_at AS "createdAt"
        FROM public_cooking_requests r JOIN users u ON u.id=r.customer_id
        ORDER BY r.created_at DESC LIMIT 30 OFFSET :offset
        """, new MapSqlParameterSource("offset", (long)page * 30));
  }
}
