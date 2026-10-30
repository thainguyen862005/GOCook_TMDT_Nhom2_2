package com.james.LMS.publiccontent;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProvinceAreasTest {
  @Test void acceptsOnlyOfficialCodesAndPreservesLeadingZero() {
    assertEquals("01", ProvinceAreas.require("01"));
    assertEquals("75,79", ProvinceAreas.requireMultiple("75,79"));
    assertEquals(34, ProvinceAreas.codes().size());
    assertThrows(ResponseStatusException.class, () -> ProvinceAreas.require("Biên Hòa"));
    assertThrows(ResponseStatusException.class, () -> ProvinceAreas.require("1"));
  }
  @Test void chefSelectionRejectsUnrecognizedDuplicateAndOversizedLists() {
    assertThrows(ResponseStatusException.class, () -> ProvinceAreas.requireMultiple("75,75"));
    assertThrows(ResponseStatusException.class, () -> ProvinceAreas.requireMultiple("75,,79"));
    assertThrows(ResponseStatusException.class, () -> ProvinceAreas.requireMultiple(""));
    assertThrows(ResponseStatusException.class, () -> ProvinceAreas.requireMultiple("01,04,08,11,12,14,15,19,20,22,24,25,31"));
  }
}
