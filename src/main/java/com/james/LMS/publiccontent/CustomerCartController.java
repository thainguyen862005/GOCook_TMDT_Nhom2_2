package com.james.LMS.publiccontent;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

/** A cart contains candidate bookings, not a payment ledger. Checkout reprices each selected menu. */
@RestController
@RequestMapping("/api/customer/cart")
@PreAuthorize("hasAuthority('ROLE_USER')")
public class CustomerCartController {
  private final NamedParameterJdbcTemplate jdbc;
  public CustomerCartController(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

  public record AddInput(@NotNull @Positive Long productId, @NotNull @Min(1) @Max(100) Integer guestCount,
                         @Size(max=500) String note) {}
  public record UpdateInput(@NotNull @Min(1) @Max(100) Integer guestCount, @Size(max=500) String note) {}

  private static long user(SecurityUserDetails actor) {
    if (actor==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return actor.getId();
  }
  private static MapSqlParameterSource p(Object... entries) {
    var p=new MapSqlParameterSource();
    for(int i=0;i<entries.length;i+=2) p.addValue((String)entries[i],entries[i+1]);
    return p;
  }

  @GetMapping
  public List<Map<String,Object>> list(@AuthenticationPrincipal SecurityUserDetails actor) {
    return jdbc.queryForList("""
      SELECT ci.id,ci.product_id AS "productId",ci.guest_count AS "guestCount",ci.note,
        p.name,p.category,p.unit_price AS "unitPrice",p.image_url AS "imageUrl",
        p.min_guests AS "minGuests",p.max_guests AS "maxGuests",
        (p.status='PUBLISHED' AND p.is_active AND ci.guest_count BETWEEN p.min_guests AND p.max_guests)
          AS available
      FROM carts c JOIN cart_items ci ON ci.cart_id=c.id AND ci.is_active=true
      JOIN products p ON p.id=ci.product_id
      WHERE c.user_id=:uid AND c.is_active=true
      ORDER BY ci.updated_at DESC,ci.id DESC LIMIT 20
      """,p("uid",user(actor)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String,Object> add(@AuthenticationPrincipal SecurityUserDetails actor,
                                @Valid @RequestBody AddInput input) {
    long uid=user(actor);
    validateProduct(input.productId(),input.guestCount());
    Long cartId=jdbc.queryForObject("""
      INSERT INTO carts(user_id) VALUES (:uid)
      ON CONFLICT (user_id) DO UPDATE SET is_active=true,updated_at=(EXTRACT(EPOCH FROM now())*1000)::bigint
      RETURNING id
      """,p("uid",uid),Long.class);
    if (cartId==null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    long count=jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id=:cart AND is_active=true",p("cart",cartId),Long.class);
    long exists=jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id=:cart AND product_id=:product AND is_active=true",
      p("cart",cartId,"product",input.productId()),Long.class);
    if(count>=20 && exists==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Giỏ hàng tối đa 20 thực đơn.");
    Long id=jdbc.queryForObject("""
      INSERT INTO cart_items(cart_id,product_id,guest_count,quantity,note,is_active)
      VALUES (:cart,:product,:guests,1,:note,true)
      ON CONFLICT (cart_id,product_id) DO UPDATE SET
        guest_count=EXCLUDED.guest_count,note=EXCLUDED.note,is_active=true,
        updated_at=(EXTRACT(EPOCH FROM now())*1000)::bigint
      RETURNING id
      """,p("cart",cartId,"product",input.productId(),"guests",input.guestCount(),"note",sanitize(input.note())),Long.class);
    return Map.of("id",id,"status","SAVED");
  }

  @PatchMapping("/{itemId}")
  @Transactional
  public Map<String,Object> update(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long itemId,
                                   @Valid @RequestBody UpdateInput input) {
    long uid=user(actor);
    Long product=jdbc.query("""
        SELECT ci.product_id FROM cart_items ci JOIN carts c ON c.id=ci.cart_id
        WHERE ci.id=:item AND c.user_id=:uid AND ci.is_active=true AND c.is_active=true
        FOR UPDATE OF ci
      """,p("uid",uid,"item",itemId),rs->rs.next()?rs.getLong(1):null);
    if(product==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thực đơn trong giỏ.");
    validateProduct(product,input.guestCount());
    jdbc.update("""
      UPDATE cart_items SET guest_count=:guests,note=:note,version=version+1,
        updated_at=(EXTRACT(EPOCH FROM now())*1000)::bigint WHERE id=:item
      """,p("guests",input.guestCount(),"note",sanitize(input.note()),"item",itemId));
    return Map.of("status","UPDATED");
  }

  @DeleteMapping("/{itemId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void remove(@AuthenticationPrincipal SecurityUserDetails actor,@PathVariable long itemId) {
    int changed=jdbc.update("""
      DELETE FROM cart_items ci USING carts c
      WHERE ci.cart_id=c.id AND ci.id=:item AND c.user_id=:uid AND c.is_active=true
      """,p("uid",user(actor),"item",itemId));
    if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Thực đơn không nằm trong giỏ.");
  }

  private void validateProduct(long productId,int guests) {
    Long eligible=jdbc.queryForObject("""
      SELECT COUNT(*) FROM products p WHERE p.id=:product AND p.is_active=true
        AND p.status='PUBLISHED' AND :guests BETWEEN p.min_guests AND p.max_guests
      """,p("product",productId,"guests",guests),Long.class);
    if(eligible==null||eligible==0) throw new ResponseStatusException(HttpStatus.CONFLICT,
      "Thực đơn không còn mở bán hoặc số khách nằm ngoài giới hạn. Hãy tải lại thực đơn.");
  }
  private static String sanitize(String text) {return text==null?"":text.trim();}
}
