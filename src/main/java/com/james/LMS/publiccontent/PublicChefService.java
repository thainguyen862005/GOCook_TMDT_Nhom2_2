package com.james.LMS.publiccontent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Public-safe Chef catalog. Never expose email, phone, or pending verification documents. */
@Service
public class PublicChefService {
  private final NamedParameterJdbcTemplate jdbc;
  public PublicChefService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  private static final String APPROVED = "cp.is_active = true AND cp.is_verified = true "
      + "AND cp.verification_status = 'APPROVED' AND u.is_active = true";
  private static final String FROM = " FROM chef_profiles cp JOIN users u ON u.id = cp.user_id ";
  private static final String MENU_STATS = """
      LEFT JOIN LATERAL (
        SELECT COUNT(*) AS menu_count, MIN(p.unit_price) AS from_price,
          STRING_AGG(DISTINCT p.category, ', ' ORDER BY p.category) AS specialties
        FROM products p WHERE p.seller_id = cp.user_id AND p.is_active = true
          AND p.status = 'PUBLISHED'
      ) m ON true
      """;
  private static final String FIELDS = "SELECT cp.user_id AS id, cp.display_name AS \"displayName\", "
      + "cp.bio, cp.service_area AS \"serviceArea\", cp.rating, u.avatar_url AS \"avatarUrl\", "
      + "m.menu_count AS \"menuCount\", m.from_price AS \"fromPrice\", "
      + "m.specialties AS specialties ";

  public Map<String, Object> search(String query, String area, String category, int page, int size, String sort) {
    if (page < 0 || page > 100000 || size < 1 || size > 30) throw new IllegalArgumentException("Phân trang đầu bếp không hợp lệ.");
    String q = safe(query, 100); String a = safe(area, 100); String c = safe(category, 100);
    String filter = " WHERE " + APPROVED
        + " AND (:q = '' OR cp.display_name ILIKE :query OR COALESCE(cp.bio,'') ILIKE :query)"
        + " AND (:area = '' OR COALESCE(cp.service_area,'') ILIKE :areaQuery)"
        + " AND (:category = '' OR EXISTS (SELECT 1 FROM products p2 WHERE p2.seller_id=cp.user_id "
        + " AND p2.is_active=true AND p2.status='PUBLISHED' AND p2.category ILIKE :categoryQuery))";
    MapSqlParameterSource args = new MapSqlParameterSource()
        .addValue("q", q).addValue("query", "%" + escapeLike(q) + "%")
        .addValue("area", a).addValue("areaQuery", "%" + escapeLike(a) + "%")
        .addValue("category", c).addValue("categoryQuery", c)
        .addValue("limit", size).addValue("offset", (long) page * size);
    long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM + filter, args, Long.class);
    String order = switch (sort == null ? "" : sort) {
      case "name" -> "cp.display_name ASC, cp.user_id ASC";
      case "menus" -> "m.menu_count DESC, cp.user_id DESC";
      default -> "cp.rating DESC, m.menu_count DESC, cp.user_id DESC";
    };
    List<Map<String, Object>> cards = jdbc.queryForList(FIELDS + FROM + MENU_STATS + filter
        + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", args);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", cards); result.put("page", page); result.put("size", size);
    result.put("totalElements", total); result.put("totalPages", (total + size - 1) / size);
    return result;
  }

  public Map<String, Object> detail(long id) {
    if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID đầu bếp không hợp lệ.");
    var args = new MapSqlParameterSource("id", id);
    Map<String, Object> chef;
    try {
      chef = new LinkedHashMap<>(jdbc.queryForMap(FIELDS + FROM + MENU_STATS
          + " WHERE " + APPROVED + " AND cp.user_id=:id", args));
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đầu bếp đang hoạt động.");
    }
    List<Map<String, Object>> menus = jdbc.queryForList("""
        SELECT p.id, p.name, p.category, p.unit_price AS "unitPrice", p.image_url AS "imageUrl",
               p.min_guests AS "minGuests", p.max_guests AS "maxGuests"
        FROM products p WHERE p.seller_id=:id AND p.is_active=true AND p.status='PUBLISHED'
        ORDER BY p.updated_at DESC, p.id DESC LIMIT 24
        """, args);
    chef.put("menus", menus);
    return chef;
  }

  public List<String> areas() {
    // Areas come from active, verified Chef profiles; don't pretend regions without Chefs exist.
    return jdbc.getJdbcTemplate().queryForList("""
        SELECT DISTINCT cp.service_area FROM chef_profiles cp JOIN users u ON u.id=cp.user_id
        WHERE cp.is_active=true AND cp.is_verified=true AND cp.verification_status='APPROVED'
          AND u.is_active=true AND cp.service_area IS NOT NULL AND TRIM(cp.service_area)<>''
        ORDER BY cp.service_area LIMIT 100
        """, String.class);
  }

  private static String safe(String input, int limit) {
    String result = input == null ? "" : input.trim();
    if (result.length() > limit) throw new IllegalArgumentException("Bộ lọc quá dài.");
    return result;
  }
  private static String escapeLike(String input) {
    // Literal LIKE matching, not wildcard expansion from user input.
    return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
