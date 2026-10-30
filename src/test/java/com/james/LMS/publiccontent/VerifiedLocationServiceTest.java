package com.james.LMS.publiccontent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class VerifiedLocationServiceTest {
  @Test void verifiedPointBindsProvinceWardCoordinatesAndCustomerAddress() {
    var wards=mock(WardDirectory.class);
    when(wards.require("75","25960")).thenReturn(new WardDirectory.Ward("75","25960","Phường Trấn Biên"));
    var service=new VerifiedLocationService(wards);
    String address="Trấn Biên, Đồng Nai, Việt Nam";
    String token=service.issue("75","25960",10.950001,106.820001,address);
    assertEquals("75|10.95000|106.82000|25960",
        service.confirmCustomer("75|10.95000|106.82000|25960|"+token,address));
    assertEquals("75|10.95000|106.82000|15|25960",
        service.confirmChef("75|10.95000|106.82000|15|25960|"+token));
    assertThrows(ResponseStatusException.class, ()->service.confirmCustomer("79|10.95000|106.82000|25960|"+token,address));
    assertThrows(ResponseStatusException.class, ()->service.confirmCustomer("75|11.95000|106.82000|25960|"+token,address));
    assertThrows(ResponseStatusException.class, ()->service.confirmCustomer("75|10.95000|106.82000|25960|"+token,"Hà Nội"));
    assertThrows(ResponseStatusException.class, ()->service.confirmCustomer("75|10.95000|106.82000|25960|FAKE",address));
  }
  @Test void geocoderMustConfirmBothAdministrativeLevelsNotJustDisplayText() throws Exception {
    var mapper=new ObjectMapper();
    var good=mapper.readTree("{\"state\":\"Đồng Nai\",\"suburb\":\"Phường Trấn Biên\"}");
    var wrongProvince=mapper.readTree("{\"state\":\"Hà Nội\",\"suburb\":\"Phường Trấn Biên\"}");
    var wrongWard=mapper.readTree("{\"state\":\"Đồng Nai\",\"suburb\":\"Phường Bình Trưng\"}");
    assertTrue(GeocodingController.matchesAdministrativeAddress(good,"Đồng Nai","Phường Trấn Biên"));
    assertFalse(GeocodingController.matchesAdministrativeAddress(wrongProvince,"Đồng Nai","Phường Trấn Biên"));
    assertFalse(GeocodingController.matchesAdministrativeAddress(wrongWard,"Đồng Nai","Phường Trấn Biên"));
    assertTrue(GeocodingController.sameRegion("TP. Hồ Chí Minh","Ho Chi Minh City"));
  }
}
