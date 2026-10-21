package com.james.LMS.publiccontent;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicCatalogController {
  private final PublicChefService chefs;
  private final JdbcTemplate jdbc;
  public PublicCatalogController(PublicChefService chefs, JdbcTemplate jdbc) {
    this.chefs = chefs; this.jdbc = jdbc;
  }
  @GetMapping("/chefs")
  public Map<String, Object> chefs(@RequestParam(defaultValue="") String q,
      @RequestParam(defaultValue="") String area, @RequestParam(defaultValue="") String category,
      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="12") int size,
      @RequestParam(defaultValue="rating") String sort) {
    return chefs.search(q, area, category, page, size, sort);
  }
  @GetMapping("/chefs/areas")
  public List<String> areas() { return chefs.areas(); }
  @GetMapping("/chefs/{id}")
  public Map<String, Object> chef(@PathVariable long id) { return chefs.detail(id); }

  /** A voucher is visible only while enabled, within its validity window, and unused capacity remains. */
  @GetMapping("/offers")
  public List<Map<String, Object>> offers() {
    return jdbc.queryForList("""
        SELECT code, description, discount_type AS "discountType",
          discount_value AS "discountValue", min_order_amount AS "minOrderAmount",
          max_discount AS "maxDiscount", ends_at AS "endsAt"
        FROM admin_vouchers
        WHERE is_active=true AND starts_at <= now() AND ends_at > now()
          AND used_count < usage_limit
        ORDER BY ends_at ASC, id DESC LIMIT 100
        """);
  }
  @GetMapping("/faqs")
  public List<Map<String, Object>> faqs() {
    return jdbc.queryForList("""
        SELECT id, question, answer, category FROM public_faqs
        WHERE is_active=true ORDER BY display_order ASC, id ASC LIMIT 100
        """);
  }
}
