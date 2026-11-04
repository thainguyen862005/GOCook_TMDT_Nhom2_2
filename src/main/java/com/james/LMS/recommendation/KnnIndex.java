package com.james.LMS.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/** Immutable, read-optimized snapshots. Build next index off the request thread. */
public final class KnnIndex {
  private KnnIndex() {}

  public record Neighbor(long id, double similarity) {}
  public record Snapshot(Map<Long, MenuDocument> menus,
                         Map<Long, Map<Integer, Double>> vectors,
                         Map<Long, List<Neighbor>> neighbors) {
    public static Snapshot empty() { return new Snapshot(Map.of(), Map.of(), Map.of()); }
  }

  private static final Comparator<Neighbor> BY_SIMILARITY =
      Comparator.comparingDouble(Neighbor::similarity).reversed().thenComparingLong(Neighbor::id);

  private static boolean sameContent(MenuDocument a, MenuDocument b) {
    return Objects.equals(a.name(), b.name())
        && Objects.equals(a.category(), b.category())
        && Objects.equals(a.dishItems(), b.dishItems())
        && Objects.equals(a.description(), b.description());
  }

  private static void upsert(Map<Long, List<Neighbor>> graph, long source,
                             long destination, double similarity, int maxNeighbors) {
    if (similarity <= 1e-9) return;
    var list = new ArrayList<>(graph.getOrDefault(source, List.of()));
    list.removeIf(n -> n.id() == destination);
    list.add(new Neighbor(destination, similarity));
    list.sort(BY_SIMILARITY);
    if (list.size() > maxNeighbors) list.subList(maxNeighbors, list.size()).clear();
    graph.put(source, List.copyOf(list));
  }

  private static Map<Long, List<Neighbor>> fullGraph(
      Map<Long, Map<Integer, Double>> vectors, int maxNeighbors) {
    var ids = new ArrayList<>(vectors.keySet());
    ids.sort(Long::compareTo);
    var graph = new HashMap<Long, List<Neighbor>>();
    for (long id : ids) graph.put(id, List.of());
    for (int i=0; i<ids.size(); i++) {
      long a=ids.get(i);
      for (int j=i+1; j<ids.size(); j++) {
        long b=ids.get(j);
        double score = MenuFeatureEncoder.cosine(vectors.get(a), vectors.get(b));
        upsert(graph, a, b, score, maxNeighbors);
        upsert(graph, b, a, score, maxNeighbors);
      }
    }
    return Map.copyOf(graph);
  }

  public static Snapshot reconcile(Snapshot before, List<MenuDocument> fresh,
                                   MenuFeatureEncoder encoder, int maxNeighbors) {
    var latest = new LinkedHashMap<Long, MenuDocument>();
    for (MenuDocument menu : fresh) {
      if (latest.put(menu.id(), menu) != null) {
        throw new IllegalArgumentException("Duplicated menu ID: " + menu.id());
      }
    }
    if (latest.equals(before.menus())) return before;

    var vectors = new HashMap<>(before.vectors());
    Set<Long> deleted = new HashSet<>(before.menus().keySet());
    deleted.removeAll(latest.keySet());
    for (long id : deleted) vectors.remove(id);

    var added = new ArrayList<Long>();
    boolean editedContent = false;
    for (MenuDocument menu : latest.values()) {
      MenuDocument old = before.menus().get(menu.id());
      if (old == null) {
        added.add(menu.id());
        vectors.put(menu.id(), encoder.encode(menu));
      } else if (!sameContent(old, menu)) {
        editedContent = true;
        vectors.put(menu.id(), encoder.encode(menu));
      }
    }

    Map<Long, List<Neighbor>> graph;
    if (!deleted.isEmpty() || editedContent || before.menus().isEmpty()) {
      // Edits/deletions invalidate old top-K rows. Rebuild NEIGHBORS only,
      // never retrain Python or change vocabulary. Runs in background.
      graph = fullGraph(vectors, maxNeighbors);
    } else if (added.isEmpty()) {
      // Only price/image/guest metadata changed. No vector computation.
      graph = before.neighbors();
    } else {
      // Fast path: compare each new menu with current vectors and update BOTH
      // directions, so customers who bought older menus can discover new ones.
      var mutable = new HashMap<>(before.neighbors());
      for (long id : latest.keySet()) mutable.putIfAbsent(id, List.of());
      added.sort(Long::compareTo);
      for (long newId : added) {
        for (long existingId : latest.keySet()) {
          if (existingId == newId) continue;
          double score = MenuFeatureEncoder.cosine(vectors.get(newId), vectors.get(existingId));
          upsert(mutable, newId, existingId, score, maxNeighbors);
          upsert(mutable, existingId, newId, score, maxNeighbors);
        }
      }
      graph = Map.copyOf(mutable);
    }
    return new Snapshot(Map.copyOf(latest), Map.copyOf(vectors), graph);
  }
}
