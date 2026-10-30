package com.james.LMS.publiccontent;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WardDirectoryTest {
  @Test void catalogHasWardsOfAllProvincesAndRejectsCrossProvinceSelection() {
    var catalog = new WardDirectory(new ObjectMapper());
    assertTrue(catalog.available(), "Install src/main/resources/administrative/wards.json before running Maven tests");
    var dongNai = catalog.wards("75");
    var hoChiMinh = catalog.wards("79");
    assertFalse(dongNai.isEmpty());
    assertFalse(hoChiMinh.isEmpty());
    var ward = dongNai.get(0);
    assertEquals("75", catalog.require("75", ward.code()).province());
    assertThrows(ResponseStatusException.class, () -> catalog.require("79", ward.code()));
  }
}
