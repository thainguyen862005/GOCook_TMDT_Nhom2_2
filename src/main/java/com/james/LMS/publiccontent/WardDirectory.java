package com.james.LMS.publiccontent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Fixed 2025+ administrative catalog, downloaded and checked by the installer.
 * No database tables or remote calls are required during matching or form validation.
 */
@Component
public class WardDirectory {
  public record Ward(String province, String code, String name) {}
  private final Map<String, List<Ward>> byProvince;
  private final Map<String, Ward> byCode;

  public WardDirectory(ObjectMapper mapper) {
    List<Ward> list;
    try (InputStream stream = WardDirectory.class.getResourceAsStream("/administrative/wards.json")) {
      if (stream == null) {
        list = List.of(); // Fail closed: setup script must install a verified dataset.
      } else {
        list = mapper.readValue(stream, new TypeReference<List<Ward>>(){});
      }
    } catch (Exception error) {
      throw new IllegalStateException("Invalid administrative ward catalog", error);
    }
    for (Ward ward : list) {
      if (!ProvinceAreas.valid(ward.province()) || !ward.code().matches("\\d{5}") || ward.name().isBlank())
        throw new IllegalStateException("Invalid ward catalog entry");
    }
    byProvince = list.stream().collect(Collectors.groupingBy(Ward::province));
    byCode = list.stream().collect(Collectors.toMap(Ward::code, ward -> ward, (a, b) -> {
      throw new IllegalStateException("Duplicate ward code " + a.code());
    }));
  }

  public boolean available() { return byCode.size() >= 3000; }
  public Ward require(String province, String wardCode) {
    if (!available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "Chưa cài bộ dữ liệu xã/phường. Vui lòng báo quản trị viên.");
    ProvinceAreas.require(province);
    Ward ward = byCode.get(wardCode);
    if (ward == null || !ward.province().equals(province))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Phường/xã không thuộc tỉnh/thành đã chọn.");
    return ward;
  }
  public List<Ward> wards(String province) {
    ProvinceAreas.require(province);
    if (!available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "Chưa cài bộ dữ liệu xã/phường. Vui lòng báo quản trị viên.");
    return byProvince.getOrDefault(province, List.of());
  }
}
