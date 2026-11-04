package com.james.LMS.publiccontent;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

/** Product reviews are different from Chef reviews. Only purchased, accepted menu items qualify. */
@RestController
public class VerifiedMenuReviewController {
 private final NamedParameterJdbcTemplate jdbc;
 public VerifiedMenuReviewController(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Input(@Min(1) @Max(5) int rating,@NotBlank @Size(min=15,max=1000) String body){}
 private static MapSqlParameterSource p(Object...x){var a=new MapSqlParameterSource();for(int i=0;i<x.length;i+=2)a.addValue((String)x[i],x[i+1]);return a;}
 private static long uid(SecurityUserDetails actor){if(actor==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return actor.getId();}

 @GetMapping("/api/customer/orders/{orderId}/menu-review/eligibility")
 @PreAuthorize("isAuthenticated()")
 public Map<String,Object> eligibility(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long orderId){
   var a=p("order",orderId,"user",uid(actor));
   Long count=jdbc.queryForObject("""
     SELECT COUNT(*) FROM orders o JOIN order_escrows e ON e.order_id=o.id
     JOIN order_items oi ON oi.order_id=o.id
     WHERE o.id=:order AND o.customer_id=:user AND o.status='COMPLETED' AND e.status='RELEASED'
       AND oi.product_id IS NOT NULL
     """,a,Long.class);
   return Map.of("eligible",count!=null&&count>0);
 }

 @PostMapping("/api/customer/orders/{orderId}/menu-review")
 @PreAuthorize("isAuthenticated()")
 @ResponseStatus(HttpStatus.CREATED)
 @Transactional
 public Map<String,Object> publish(@AuthenticationPrincipal SecurityUserDetails actor,
    @PathVariable long orderId,@Valid @RequestBody Input input){
   long user=uid(actor);String body=input.body().trim();if(body.length()<15)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
   Long id=jdbc.query("""
      INSERT INTO menu_reviews(order_id,product_id,customer_id,rating,body)
      SELECT o.id,oi.product_id,o.customer_id,:rating,:body FROM orders o
      JOIN order_escrows e ON e.order_id=o.id
      JOIN order_items oi ON oi.order_id=o.id AND oi.product_id IS NOT NULL
      WHERE o.id=:order AND o.customer_id=:user AND o.status='COMPLETED' AND e.status='RELEASED'
      ORDER BY oi.id ASC LIMIT 1
      ON CONFLICT(order_id) DO NOTHING RETURNING id
     """,p("order",orderId,"user",user,"rating",input.rating(),"body",body),rs->rs.next()?rs.getLong(1):null);
   if(id==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ đánh giá một lần khi thực đơn đã được mua và nghiệm thu.");
   return Map.of("id",id,"status","PUBLISHED");
 }

 @GetMapping("/api/customer/orders/{orderId}/menu-review")
 @PreAuthorize("isAuthenticated()")
 public List<Map<String,Object>> own(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long orderId){
  return jdbc.queryForList("""
     SELECT r.id,r.product_id AS "productId",r.rating,r.body,r.created_at AS "createdAt"
     FROM menu_reviews r JOIN orders o ON o.id=r.order_id
     WHERE r.order_id=:order AND o.customer_id=:user
    """,p("order",orderId,"user",uid(actor)));
 }

 @GetMapping("/api/public/menus/{menuId}/reviews")
 public List<Map<String,Object>> publicReviews(@PathVariable long menuId){
  if(menuId<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  return jdbc.queryForList("""
    SELECT r.id,r.rating,r.body,r.created_at AS "createdAt",u.username AS "customerName"
    FROM menu_reviews r JOIN users u ON u.id=r.customer_id
    JOIN products p ON p.id=r.product_id
    WHERE r.product_id=:id AND p.status='PUBLISHED' AND p.is_active=true
    ORDER BY r.created_at DESC,r.id DESC LIMIT 30
    """,p("id",menuId));
 }
}
