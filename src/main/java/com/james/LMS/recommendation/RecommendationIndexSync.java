package com.james.LMS.recommendation;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Polls read-only catalog in its own scheduler, never during a Chef save request. */
@Service
public class RecommendationIndexSync {
  private static final Logger log = LoggerFactory.getLogger(RecommendationIndexSync.class);
  private static final int MAX_MENUS = 2000;
  private final NamedParameterJdbcTemplate jdbc;
  private final MenuFeatureEncoder encoder;
  private final KnnConfig config;

  // Volatile is the publication barrier: customer requests only read complete snapshots.
  private volatile KnnIndex.Snapshot snapshot = KnnIndex.Snapshot.empty();

  public RecommendationIndexSync(NamedParameterJdbcTemplate jdbc, KnnConfig config) {
    this.jdbc = jdbc;
    this.config = config;
    this.encoder = new MenuFeatureEncoder(config);
  }

  public KnnIndex.Snapshot snapshot() { return snapshot; }

  @Scheduled(initialDelayString = "${gocook.recommendation.initial-delay-ms:1500}",
             fixedDelayString = "${gocook.recommendation.sync-delay-ms:30000}",
             scheduler = "recommendationScheduler")
  public void synchronize() {
    long start = System.nanoTime();
    try {
      List<MenuDocument> menus = jdbc.query("""
          SELECT p.id, p.name, p.category, p.dish_items, p.description,
                 p.image_url, p.unit_price, p.min_guests, p.max_guests,
                 COALESCE(u.username, '') AS seller_name
          FROM products p LEFT JOIN users u ON u.id=p.seller_id
          WHERE p.is_active=TRUE AND p.status='PUBLISHED' AND p.seller_id IS NOT NULL
          ORDER BY p.id LIMIT 2001
          """, (rs, row) -> new MenuDocument(
              rs.getLong("id"), rs.getString("name"), rs.getString("category"),
              rs.getString("dish_items"), rs.getString("description"),
              rs.getString("image_url"), rs.getBigDecimal("unit_price").toPlainString(),
              rs.getInt("min_guests"), rs.getInt("max_guests"), rs.getString("seller_name")));
      if (menus.size() > MAX_MENUS) {
        log.warn("KNN catalog exceeds {} menus; keeping previous snapshot", MAX_MENUS);
        return;
      }
      KnnIndex.Snapshot next = KnnIndex.reconcile(snapshot, menus, encoder, config.maxNeighbors());
      if (next != snapshot) {
        snapshot = next;
        long ms = (System.nanoTime() - start) / 1_000_000;
        log.info("KNN index synced: {} published menus in {} ms", next.menus().size(), ms);
      }
    } catch (Exception e) {
      // Never block startup, login, Chef CRUD, bookings or payments because of KNN.
      log.warn("KNN sync failed; keeping previous snapshot: {}", e.toString());
    }
  }
}
