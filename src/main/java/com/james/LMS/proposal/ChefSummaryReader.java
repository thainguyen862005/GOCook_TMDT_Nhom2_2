package com.james.LMS.proposal;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Public facts about chefs (users.id) that a customer may see next to a proposal. */
@Component
@RequiredArgsConstructor
public class ChefSummaryReader {

  public record ChefSummary(
      Long id, String displayName, BigDecimal rating, boolean verified, boolean active) {}

  private static final String SQL =
      """
      SELECT u.id,
             COALESCE(cp.display_name, u.username) AS display_name,
             COALESCE(cp.rating, 0) AS rating,
             COALESCE(cp.is_verified, false) AS verified,
             (COALESCE(cp.is_active, false) AND u.is_active) AS active
      FROM users u LEFT JOIN chef_profiles cp ON cp.user_id = u.id
      WHERE u.id IN (:ids)
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public Map<Long, ChefSummary> load(Collection<Long> chefIds) {
    Map<Long, ChefSummary> found = new LinkedHashMap<>();
    if (chefIds.isEmpty()) {
      return found;
    }
    jdbc.query(
        SQL,
        new MapSqlParameterSource("ids", chefIds),
        (row, number) ->
            found.put(
                row.getLong("id"),
                new ChefSummary(
                    row.getLong("id"),
                    row.getString("display_name"),
                    row.getBigDecimal("rating"),
                    row.getBoolean("verified"),
                    row.getBoolean("active"))));
    return found;
  }
}
