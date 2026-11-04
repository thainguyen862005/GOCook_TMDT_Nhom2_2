package com.james.LMS.recommendation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuFeatureEncoderTest {
  private final MenuFeatureEncoder encoder = new MenuFeatureEncoder(KnnConfig.defaults());

  @Test void vietnameseNormalizationMatchesPython() {
    assertEquals("dau hu nam va com", MenuFeatureEncoder.normalize("Đậu hũ, nấm và CƠM"));
    assertEquals(5184, encoder.hashToken("g: cha"));
    assertEquals(12444, encoder.hashToken("cat:mon_chay"));
  }

  @Test void cosineMatchesPythonReferenceFixture() {
    var a = new MenuDocument(1, "Mâm cơm chay", "Món chay", "Đậu hũ nấm",
        "Bữa cơm nhà thanh đạm", null, "100000", 2, 8, "Chef A");
    var b = new MenuDocument(2, "Lẩu nấm chay", "Món chay", "Đậu hũ rau củ",
        "Ăn chay gia đình", null, "120000", 2, 8, "Chef B");
    var c = new MenuDocument(3, "BBQ hải sản", "Hải sản", "Tôm mực",
        "Nướng BBQ", null, "220000", 2, 8, "Chef C");
    var vecA = encoder.encode(a);
    assertEquals(70, vecA.size());
    assertEquals(0.8442357037168398, MenuFeatureEncoder.cosine(vecA, encoder.encode(b)), 1e-9);
    assertEquals(0.012837348738230912, MenuFeatureEncoder.cosine(vecA, encoder.encode(c)), 1e-9);
  }
}
