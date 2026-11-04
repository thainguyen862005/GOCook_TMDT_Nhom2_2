package com.james.LMS.recommendation;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

/** Exactly mirrors recommendation-ml/feature_encoder.py, including CRC32. */
public final class MenuFeatureEncoder {
  private final KnnConfig cfg;

  public MenuFeatureEncoder(KnnConfig cfg) { this.cfg = cfg; }

  public static String normalize(String input) {
    if (input == null) return "";
    String text = input.toLowerCase(Locale.ROOT).replace('đ', 'd');
    text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return text.replaceAll("[^a-z0-9]+", " ").trim();
  }

  public int hashToken(String token) {
    CRC32 crc = new CRC32();
    crc.update(token.getBytes(StandardCharsets.US_ASCII));
    return (int) (crc.getValue() % cfg.hashDimension());
  }

  private void addText(Map<Integer, Double> counts, String input, double weight) {
    if (weight == 0) return;
    String normalized = normalize(input);
    if (normalized.isEmpty()) return;
    for (String word : normalized.split(" ")) {
      String padded = " " + word + " ";
      for (int n = cfg.minNgram(); n <= cfg.maxNgram(); n++) {
        for (int start = 0; start + n <= padded.length(); start++) {
          int idx = hashToken("g:" + padded.substring(start, start + n));
          counts.merge(idx, weight, Double::sum);
        }
      }
    }
  }

  public Map<Integer, Double> encode(MenuDocument menu) {
    Map<Integer, Double> counts = new HashMap<>();
    addText(counts, menu.category(), cfg.weight("category"));
    addText(counts, menu.dishItems(), cfg.weight("dishItems"));
    addText(counts, menu.name(), cfg.weight("name"));
    addText(counts, menu.description(), cfg.weight("description"));
    String category = normalize(menu.category());
    if (!category.isEmpty()) {
      int idx = hashToken("cat:" + category.replace(' ', '_'));
      counts.merge(idx, cfg.weight("categoryExact"), Double::sum);
    }
    double norm = Math.sqrt(counts.values().stream().mapToDouble(x -> x*x).sum());
    if (norm == 0) return Map.of();
    counts.replaceAll((k, value) -> value / norm);
    return Map.copyOf(counts);
  }

  public static double cosine(Map<Integer, Double> a, Map<Integer, Double> b) {
    if (a.isEmpty() || b.isEmpty()) return 0;
    if (a.size() > b.size()) return cosine(b, a);
    double score = 0;
    for (var entry : a.entrySet()) score += entry.getValue() * b.getOrDefault(entry.getKey(), 0.0);
    return Math.min(1.0, Math.max(0.0, score));
  }
}
