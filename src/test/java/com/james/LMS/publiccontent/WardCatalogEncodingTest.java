package com.james.LMS.publiccontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/** Regression test: Vietnamese ward names must not be UTF-8/Latin-1 mojibake. */
class WardCatalogEncodingTest {
  @Test
  void wardNamesAreReadableVietnameseAndAdministrativeCodesRemainValid() {
    var directory = new WardDirectory(new ObjectMapper());
    assertTrue(directory.available());
    assertEquals("Phường Ba Đình", directory.require("01", "00004").name());
    assertEquals("Phường Ngọc Hà", directory.require("01", "00008").name());
    assertEquals("Phường Hoàn Kiếm", directory.require("01", "00070").name());

    int count = 0;
    for (var province : ProvinceAreas.codes()) {
      for (var ward : directory.wards(province)) {
        String label = ward.name();
        assertTrue(label.startsWith("Phường ") || label.startsWith("Xã ") || label.startsWith("Đặc khu "),
            () -> "Unreadable ward name: " + ward.code() + " = " + label);
        assertTrue(label.codePoints().noneMatch(Character::isISOControl),
            () -> "Control character in ward name: " + ward.code());
        assertEquals(Normalizer.normalize(label, Normalizer.Form.NFC), label,
            () -> "Non-NFC ward name: " + ward.code());
        count++;
      }
    }
    assertEquals(3321, count);
  }
}
