package com.james.LMS.publiccontent;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.media.ImageFileValidator;
import com.james.LMS.service.CloudinaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
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
  public CookingRequestController(NamedParameterJdbcTemplate jdbc, CloudinaryService cloudinary) {
    this.jdbc = jdbc; this.cloudinary = cloudinary;
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
      @NotNull @DecimalMin("1") BigDecimal budgetMax) {}

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
    byte[] imageBytes = image == null || image.isEmpty() ? null : ImageFileValidator.validate(image);
    CloudinaryService.MenuImage uploaded = null;
    // Upload first; delete on failed INSERT to avoid orphaned media.
    if (imageBytes != null) uploaded = cloudinary.uploadMenuImage(imageBytes);
    String code = "REQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
    try {
      jdbc.update("""
          INSERT INTO public_cooking_requests(request_code,customer_id,desired_dishes,cuisine,
            adults,children,avoid_ingredients,spice_level,meal_date,meal_time,
            kitchen_address,equipment_available,budget_min,budget_max,reference_image_url,status)
          VALUES (:code,:customer,:dishes,:cuisine,:adults,:children,:avoid,:spice,
            :mealDate,:mealTime,:address,:equipment,:budgetMin,:budgetMax,:image,'PENDING')
          """, new MapSqlParameterSource().addValue("code", code).addValue("customer", actor.getId())
          .addValue("dishes", data.desiredDishes().trim()).addValue("cuisine", data.cuisine().trim())
          .addValue("adults", data.adults()).addValue("children", data.children())
          .addValue("avoid", data.avoid()).addValue("spice", data.spice())
          .addValue("mealDate", data.mealDate()).addValue("mealTime", data.mealTime())
          .addValue("address", data.address().trim()).addValue("equipment", data.equipment())
          .addValue("budgetMin", data.budgetMin()).addValue("budgetMax", data.budgetMax())
          .addValue("image", uploaded == null ? null : uploaded.url()));
    } catch (RuntimeException error) {
      if (uploaded != null) {
        try { cloudinary.deleteMenuImage(uploaded.publicId()); }
        catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
      }
      throw error;
    }
    return Map.of("requestCode", code, "status", "PENDING");
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
