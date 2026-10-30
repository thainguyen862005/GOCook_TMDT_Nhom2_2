package com.james.LMS.publiccontent;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ServiceGeoAreaTest {
  private static final String CHEF = "75|10.95000|106.82000|15|25960";
  @Test void closeWithinRadiusAndFarAwayRejected() {
    assertTrue(ServiceGeoArea.matches(CHEF,"75|10.95500|106.82500|25961"));
    assertFalse(ServiceGeoArea.matches(CHEF,"75|11.45000|107.21000|25961"));
  }
  @Test void nearbyAcrossProvinceBorderAllowedButDistantStillRejected() {
    assertTrue(ServiceGeoArea.matches(CHEF,"79|10.95500|106.82500|26701"));
    assertFalse(ServiceGeoArea.matches(CHEF,"79|11.95500|108.82500|26701"));
  }
  @Test void legacyAndGarbageFailClosedAndNeverThrowInMatching() {
    for (String bad : new String[]{"75,79","75","","75|NaN|106.8|20","75|10.1|106.2|999"})
      assertFalse(ServiceGeoArea.matches(bad,"75|10.95500|106.82500|25961"));
    assertFalse(ServiceGeoArea.matches("75|10.95000|106.82000|15", "75|10.95500|106.82500|25961"));
    assertFalse(ServiceGeoArea.matches(CHEF, "75|10.95500|106.82500"));
    assertThrows(ResponseStatusException.class,()->ServiceGeoArea.chef("75,79"));
    assertThrows(ResponseStatusException.class,()->ServiceGeoArea.customer("75"));
  }
  @Test void respectsBoundaryAndRejectsMalformedCoordinates() {
    assertEquals(0,ServiceGeoArea.distanceKm(ServiceGeoArea.customer("01|21.02|105.8"),ServiceGeoArea.customer("01|21.02|105.8")),1e-7);
    assertThrows(ResponseStatusException.class,()->ServiceGeoArea.customer("75|Infinity|106"));
    assertThrows(ResponseStatusException.class,()->ServiceGeoArea.chef("75|10.5|106.8|11"));
    assertThrows(ResponseStatusException.class,()->ServiceGeoArea.chef("75|10.5|106.8|5|EXTRA"));
  }
}
