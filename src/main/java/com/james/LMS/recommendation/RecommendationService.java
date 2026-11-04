package com.james.LMS.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {
  private final NamedParameterJdbcTemplate jdbc;
  private final RecommendationIndexSync index;
  private final KnnConfig config;

  public RecommendationService(NamedParameterJdbcTemplate jdbc,
                               RecommendationIndexSync index, KnnConfig config) {
    this.jdbc = jdbc;
    this.index = index;
    this.config = config;
  }

  public record RecommendedMenu(long id, String name, String category, String unitPrice,
                                int minGuests, int maxGuests, String imageUrl,
                                String sellerName, int matchPercent, String reason, String basis) {}
  private record Ranked(long menuId, double similarity, String reason) {}

  /** Similar published menus from the current in-memory KNN index. No customer data. */
  public List<RecommendedMenu> similar(long menuId, int limit) {
    var snapshot = index.snapshot();
    if (!snapshot.menus().containsKey(menuId)) return List.of();
    return snapshot.neighbors().getOrDefault(menuId, List.of()).stream()
        .filter(n -> n.similarity() > 1e-9 && snapshot.menus().containsKey(n.id()))
        .limit(limit)
        .map(n -> {
          MenuDocument m = snapshot.menus().get(n.id());
          return new RecommendedMenu(m.id(), m.name(), m.category(), m.unitPrice(),
              m.minGuests(), m.maxGuests(), m.imageUrl(), m.sellerName(),
              (int) Math.round(n.similarity() * 100),
              "Tương tự thực đơn đang xem", "SIMILAR");
        }).toList();
  }

  @Transactional(readOnly = true)
  public List<RecommendedMenu> recommend(long customerId, int limit, int k) {
    var snapshot = index.snapshot();
    if (snapshot.menus().isEmpty()) return List.of();
    List<Long> history = jdbc.queryForList("""
        SELECT oi.product_id FROM order_items oi
        JOIN orders o ON o.id=oi.order_id
        JOIN order_escrows e ON e.order_id=o.id
        WHERE o.customer_id=:userId AND o.service_type='MENU'
          AND o.status='COMPLETED' AND e.status='RELEASED'
          AND oi.product_id IS NOT NULL
        GROUP BY oi.product_id ORDER BY MAX(o.created_at) DESC LIMIT 80
        """, new MapSqlParameterSource("userId", customerId), Long.class);
    // Cold-start option: a customer can express interest by adding a menu to
    // their own cart. This is NOT treated as a verified purchase in evaluation.
    String basis = "PURCHASE";
    if (history.isEmpty()) {
      history = jdbc.queryForList("""
          SELECT ci.product_id FROM carts c
          JOIN cart_items ci ON ci.cart_id=c.id
          WHERE c.user_id=:userId AND c.is_active=TRUE AND ci.is_active=TRUE
          ORDER BY ci.updated_at DESC LIMIT 20
          """, new MapSqlParameterSource("userId", customerId), Long.class);
      basis = "CART";
    }
    if (history.isEmpty()) return List.of();
    final String sourceBasis = basis;
    Set<Long> purchased = new HashSet<>(history);
    Map<Long, Ranked> candidateScores = new HashMap<>();
    for (long purchasedMenuId : history) {
      MenuDocument original = snapshot.menus().get(purchasedMenuId);
      if (original == null) continue;  // Old unavailable product, no public card.
      List<KnnIndex.Neighbor> nearest = snapshot.neighbors().getOrDefault(purchasedMenuId, List.of());
      int accepted = 0;
      for (KnnIndex.Neighbor neighbor : nearest) {
        if (accepted >= k) break;
        if (purchased.contains(neighbor.id()) || neighbor.similarity() <= 1e-9) continue;
        MenuDocument candidate = snapshot.menus().get(neighbor.id());
        if (candidate == null) continue;
        accepted++;
        // Score = closest menu among user's verified purchases, not a probability.
        var score = new Ranked(candidate.id(), neighbor.similarity(),
            "Tương tự " + original.name());
        Ranked old = candidateScores.get(candidate.id());
        if (old == null || score.similarity() > old.similarity()) {
          candidateScores.put(candidate.id(), score);
        }
      }
    }
    List<Ranked> sorted = new ArrayList<>(candidateScores.values());
    sorted.sort(Comparator.comparingDouble(Ranked::similarity).reversed()
        .thenComparingLong(Ranked::menuId));
    return sorted.stream().limit(limit).map(row -> {
      MenuDocument m = snapshot.menus().get(row.menuId());
      return new RecommendedMenu(m.id(), m.name(), m.category(), m.unitPrice(),
          m.minGuests(), m.maxGuests(), m.imageUrl(), m.sellerName(),
          (int)Math.round(row.similarity() * 100), row.reason(), sourceBasis);
    }).toList();
  }
}
