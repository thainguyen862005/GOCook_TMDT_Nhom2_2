package com.james.LMS.publiccontent;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Verified, one-per-completed-order customer reviews. No anonymous or purchased ratings. */
@RestController
@RequiredArgsConstructor
public class VerifiedReviewController {
  private final NamedParameterJdbcTemplate jdbc;
  public record Input(@Min(1) @Max(5) int rating,
                      @NotBlank @Size(min=15,max=1000) String body) {}

  @PostMapping("/api/customer/orders/{orderId}/review")
  @PreAuthorize("isAuthenticated()")
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> create(@AuthenticationPrincipal SecurityUserDetails actor,
      @PathVariable long orderId, @Valid @RequestBody Input input) {
    if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    String body=input.body().trim();
    if (body.length()<15) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhận xét cần từ 15 ký tự.");
    var params=new MapSqlParameterSource().addValue("order",orderId).addValue("customer",actor.getId())
      .addValue("rating",input.rating()).addValue("body",body);
    // Source of truth: completed order, legitimate escrow payout and account ownership.
    List<Long> created=jdbc.queryForList("""
      INSERT INTO order_reviews(order_id,chef_id,customer_id,rating,body)
      SELECT o.id,o.chef_id,o.customer_id,:rating,:body FROM orders o
      JOIN order_escrows e ON e.order_id=o.id
      WHERE o.id=:order AND o.customer_id=:customer AND o.chef_id IS NOT NULL
        AND o.status='COMPLETED' AND e.status='RELEASED'
      ON CONFLICT (order_id) DO NOTHING RETURNING id
      """,params,Long.class);
    if (created.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,
        "Chỉ được nhận xét một lần sau khi đơn được hoàn tất và đã nghiệm thu.");
    jdbc.update("""
      UPDATE chef_profiles cp SET rating=sub.avg_rating,
        updated_at=(EXTRACT(EPOCH FROM now())*1000)::bigint
      FROM (SELECT chef_id,ROUND(AVG(rating),2) AS avg_rating FROM order_reviews
        WHERE chef_id=(SELECT chef_id FROM orders WHERE id=:order) GROUP BY chef_id) sub
      WHERE cp.user_id=sub.chef_id
      """,params);
    return Map.of("id",created.get(0),"status","PUBLISHED");
  }

  @GetMapping("/api/customer/orders/{orderId}/review")
  @PreAuthorize("isAuthenticated()")
  public List<Map<String,Object>> own(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long orderId) {
    if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return jdbc.queryForList("""
      SELECT r.id,r.rating,r.body,r.created_at AS "createdAt" FROM order_reviews r
      JOIN orders o ON o.id=r.order_id
      WHERE r.order_id=:order AND o.customer_id=:customer
      """,new MapSqlParameterSource("order",orderId).addValue("customer",actor.getId()));
  }

  @GetMapping("/api/public/chefs/{chefId}/reviews")
  public List<Map<String,Object>> chefReviews(@PathVariable long chefId) {
    if (chefId<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return jdbc.queryForList("""
      SELECT r.id,r.rating,r.body,r.created_at AS "createdAt",u.username AS "customerName"
      FROM order_reviews r JOIN users u ON u.id=r.customer_id
      JOIN chef_profiles cp ON cp.user_id=r.chef_id
      WHERE r.chef_id=:chef AND cp.is_active=true AND cp.is_verified=true
        AND cp.verification_status='APPROVED'
      ORDER BY r.created_at DESC,r.id DESC LIMIT 20
      """,new MapSqlParameterSource("chef",chefId));
  }
}
