package com.james.LMS.recommendation;

import java.util.Map;

/** Portable, ID-independent model configuration exported from Python. */
public record KnnConfig(
    int schemaVersion, String algorithm, int hashDimension, int minNgram,
    int maxNgram, int defaultK, int maxNeighbors, Map<String, Double> fieldWeights) {

  public KnnConfig {
    if (schemaVersion != 2 || !"HASHED_CHAR_NGRAM_COSINE_ITEM_KNN".equals(algorithm)
        || hashDimension < 1024 || hashDimension > 65536 || minNgram < 2
        || maxNgram > 6 || minNgram > maxNgram || defaultK < 1
        || maxNeighbors < defaultK || maxNeighbors > 50 || fieldWeights == null) {
      throw new IllegalArgumentException("Unsupported GO Cook KNN config");
    }
    for (String key : new String[]{"name", "category", "dishItems", "description", "categoryExact"}) {
      Double value = fieldWeights.get(key);
      if (value == null || !Double.isFinite(value) || value < 0 || value > 20) {
        throw new IllegalArgumentException("Invalid KNN weight: " + key);
      }
    }
    fieldWeights = Map.copyOf(fieldWeights);
  }

  public double weight(String key) { return fieldWeights.get(key); }

  public static KnnConfig defaults() {
    return new KnnConfig(2, "HASHED_CHAR_NGRAM_COSINE_ITEM_KNN", 16384, 3, 5, 5, 30,
        Map.of("name", 1.3, "category", 2.5, "dishItems", 2.0,
            "description", 0.45, "categoryExact", 4.0));
  }
}
