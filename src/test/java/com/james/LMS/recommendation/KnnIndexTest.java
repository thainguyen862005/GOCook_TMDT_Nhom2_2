package com.james.LMS.recommendation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnnIndexTest {
  private final MenuFeatureEncoder encoder = new MenuFeatureEncoder(KnnConfig.defaults());
  private static MenuDocument menu(long id, String name, String category, String dishes) {
    return new MenuDocument(id, name, category, dishes, "Bữa cơm ngon", null, "100000", 2, 8, "Chef");
  }

  @Test void newPublishedMenuUpdatesOldMenusNeighboursWithoutRetraining() {
    var a = menu(10, "Mâm chay", "Món chay", "Đậu hũ nấm");
    var b = menu(20, "BBQ biển", "Hải sản", "Tôm nướng");
    var newMenu = menu(30, "Lẩu chay", "Món chay", "Nấm đậu hũ");
    var old = KnnIndex.reconcile(KnnIndex.Snapshot.empty(), List.of(a,b), encoder, 5);
    var next = KnnIndex.reconcile(old, List.of(a,b,newMenu), encoder, 5);
    assertTrue(next.neighbors().get(10L).stream().anyMatch(x -> x.id() == 30L));
    assertTrue(next.neighbors().get(30L).stream().anyMatch(x -> x.id() == 10L));
    assertSame(next, KnnIndex.reconcile(next, List.of(a,b,newMenu), encoder, 5));
  }

  @Test void editingAndHidingMenuRefreshesGraphSafely() {
    var a = menu(10,"Mâm chay","Món chay","Nấm đậu hũ");
    var b = menu(20,"BBQ","Hải sản","Tôm mực");
    var start = KnnIndex.reconcile(KnnIndex.Snapshot.empty(), List.of(a,b), encoder, 5);
    var edited = menu(20,"Lẩu chay","Món chay","Nấm đậu hũ");
    var changed = KnnIndex.reconcile(start, List.of(a,edited), encoder, 5);
    assertNotEquals(start.vectors().get(20L), changed.vectors().get(20L));
    assertTrue(changed.neighbors().get(10L).get(0).similarity()
        > start.neighbors().get(10L).get(0).similarity());
    var hidden = KnnIndex.reconcile(changed, List.of(a), encoder, 5);
    assertFalse(hidden.menus().containsKey(20L));
    assertTrue(hidden.neighbors().get(10L).isEmpty());
  }

  @Test void priceChangeDoesNotRevectorize() {
    var old = menu(1,"Món chay","Món chay","Đậu hũ");
    var index = KnnIndex.reconcile(KnnIndex.Snapshot.empty(), List.of(old), encoder, 5);
    var revised = new MenuDocument(1,old.name(),old.category(),old.dishItems(),old.description(),
        old.imageUrl(),"200000",old.minGuests(),old.maxGuests(),old.sellerName());
    var next = KnnIndex.reconcile(index, List.of(revised), encoder, 5);
    assertSame(index.vectors().get(1L), next.vectors().get(1L));
    assertSame(index.neighbors(), next.neighbors());
    assertEquals("200000", next.menus().get(1L).unitPrice());
  }
}
