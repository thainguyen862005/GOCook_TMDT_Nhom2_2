package com.james.LMS.recommendation;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendationServiceTest {
  private static MenuDocument menu(long id, String name, String dishes) {
    return new MenuDocument(id, name, "Món chay", dishes, "Bữa ăn chay",
        null, "100000", 2, 8, "Chef");
  }

  @Test void skipsPurchasedNeighborsBeforeApplyingKLimit() {
    var a = menu(10, "Lẩu nấm", "nấm đậu hũ");
    var b = menu(20, "Lẩu nấm", "nấm đậu hũ");
    var c = menu(30, "Lẩu chay", "nấm rau củ");
    var snapshot = KnnIndex.reconcile(KnnIndex.Snapshot.empty(), List.of(a, b, c),
        new MenuFeatureEncoder(KnnConfig.defaults()), 30);

    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var index = mock(RecommendationIndexSync.class);
    when(index.snapshot()).thenReturn(snapshot);
    when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(List.of(10L, 20L));

    var service = new RecommendationService(jdbc, index, KnnConfig.defaults());
    var menus = service.recommend(42L, 3, 1);

    assertEquals(1, menus.size(), "A purchased top-1 neighbor must not hide a valid next neighbor");
    assertEquals(30L, menus.get(0).id());
    assertEquals("PURCHASE", menus.get(0).basis());
    assertTrue(menus.get(0).matchPercent() > 0);
  }

  @Test void newPublishedMenuCanBeRecommendedWithoutRetraining() {
    var a = menu(10, "Mâm chay", "nấm đậu hũ");
    var b = menu(20, "Món biển", "tôm mực");
    var newMenu = menu(30, "Lẩu chay", "nấm đậu hũ rau củ");
    var encoder = new MenuFeatureEncoder(KnnConfig.defaults());
    var first = KnnIndex.reconcile(KnnIndex.Snapshot.empty(), List.of(a, b), encoder, 30);
    var latest = KnnIndex.reconcile(first, List.of(a, b, newMenu), encoder, 30);

    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var index = mock(RecommendationIndexSync.class);
    when(index.snapshot()).thenReturn(latest);
    when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(List.of(10L));

    var suggestions = new RecommendationService(jdbc, index, KnnConfig.defaults())
        .recommend(42L, 5, 5);
    assertTrue(suggestions.stream().anyMatch(menu -> menu.id() == 30L),
        "A newly published similar menu should be eligible after index synchronization");
    assertTrue(suggestions.stream().noneMatch(menu -> menu.id() == 10L),
        "A purchased menu should not appear in personalized recommendations");
  }
}
