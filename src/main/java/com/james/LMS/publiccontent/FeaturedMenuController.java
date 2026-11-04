package com.james.LMS.publiccontent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public featured menus based on recorded completed bookings, not personalized recommendations. */
@RestController
@RequestMapping("/api/public/featured-menus")
public class FeaturedMenuController {
  private final NamedParameterJdbcTemplate jdbc;

  public FeaturedMenuController(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping
  public List<Map<String, Object>> featured(@RequestParam(defaultValue = "3") int size) {
    if (size < 1 || size > 12) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số thực đơn phải từ 1 đến 12.");
    }

    // Only paid-out, completed bookings count. An empty history simply sorts by latest update.
    List<Map<String, Object>> rows = jdbc.queryForList("""
      SELECT p.id, p.name, p.category, p.unit_price AS "unitPrice",
             p.min_guests AS "minGuests", p.max_guests AS "maxGuests",
             p.dish_items AS "dishItems", p.image_url AS "imageUrl",
             COALESCE(u.username, '') AS "sellerName",
             COALESCE(b.booking_count, 0) AS "bookingCount"
      FROM products p
      LEFT JOIN users u ON u.id = p.seller_id
      LEFT JOIN (
        SELECT oi.product_id, COUNT(DISTINCT o.id) AS booking_count
        FROM order_items oi
        JOIN orders o ON o.id = oi.order_id
        JOIN order_escrows e ON e.order_id = o.id
        WHERE o.status = 'COMPLETED' AND e.status = 'RELEASED'
          AND oi.product_id IS NOT NULL
        GROUP BY oi.product_id
      ) b ON b.product_id = p.id
      WHERE p.is_active = true AND p.status = 'PUBLISHED' AND p.seller_id IS NOT NULL
      ORDER BY COALESCE(b.booking_count, 0) DESC, p.updated_at DESC, p.id DESC
      LIMIT :size
      """, new MapSqlParameterSource("size", size));

    List<Map<String, Object>> result = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      var item = new LinkedHashMap<>(row);
      long bookings = ((Number) row.get("bookingCount")).longValue();
      item.put("featuredReason", bookings > 0
        ? "Đã ghi nhận " + bookings + " lượt đặt hoàn thành"
        : "Thực đơn mới cập nhật");
      result.add(item);
    }
    return result;
  }
}
