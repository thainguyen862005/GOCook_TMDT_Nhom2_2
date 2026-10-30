package com.james.LMS.publiccontent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Official province identifiers (Decision 19/2025/QD-TTg). Code, never display label, is persisted. */
public final class ProvinceAreas {
  private ProvinceAreas() {}
  private static final Map<String,String> NAMES = new LinkedHashMap<>();
  static {
    Map.ofEntries(
      Map.entry("01", "Hà Nội"),
      Map.entry("04", "Cao Bằng"),
      Map.entry("08", "Tuyên Quang"),
      Map.entry("11", "Điện Biên"),
      Map.entry("12", "Lai Châu"),
      Map.entry("14", "Sơn La"),
      Map.entry("15", "Lào Cai"),
      Map.entry("19", "Thái Nguyên"),
      Map.entry("20", "Lạng Sơn"),
      Map.entry("22", "Quảng Ninh"),
      Map.entry("24", "Bắc Ninh"),
      Map.entry("25", "Phú Thọ"),
      Map.entry("31", "Hải Phòng"),
      Map.entry("33", "Hưng Yên"),
      Map.entry("37", "Ninh Bình"),
      Map.entry("38", "Thanh Hóa"),
      Map.entry("40", "Nghệ An"),
      Map.entry("42", "Hà Tĩnh"),
      Map.entry("44", "Quảng Trị"),
      Map.entry("46", "Huế"),
      Map.entry("48", "Đà Nẵng"),
      Map.entry("51", "Quảng Ngãi"),
      Map.entry("52", "Gia Lai"),
      Map.entry("56", "Khánh Hòa"),
      Map.entry("66", "Đắk Lắk"),
      Map.entry("68", "Lâm Đồng"),
      Map.entry("75", "Đồng Nai"),
      Map.entry("79", "TP. Hồ Chí Minh"),
      Map.entry("80", "Tây Ninh"),
      Map.entry("82", "Đồng Tháp"),
      Map.entry("86", "Vĩnh Long"),
      Map.entry("91", "An Giang"),
      Map.entry("92", "Cần Thơ"),
      Map.entry("96", "Cà Mau")
    ).forEach((code,name) -> NAMES.put(code,name));
  }
  public static List<String> codes() { return NAMES.keySet().stream().sorted().toList(); }
  public static boolean valid(String code) { return code != null && NAMES.containsKey(code); }
  public static String label(String code) { return NAMES.getOrDefault(code, code); }
  public static String require(String value) {
    if (!valid(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn tỉnh/thành trong danh sách chuẩn.");
    return value;
  }
  /** Existing service_area varchar(500) stores up to 12 distinct province codes, e.g. 75,79. */
  public static String requireMultiple(String value) {
    if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn ít nhất một khu vực phục vụ.");
    var codes = List.of(value.split(",", -1));
    if (codes.size() > 12 || codes.stream().distinct().count() != codes.size() || codes.stream().anyMatch(c -> !valid(c)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khu vực phục vụ không hợp lệ (tối đa 12 tỉnh/thành, không trùng mã).");
    return String.join(",", codes);
  }
  public static String describe(String codes) {
    if (codes == null || codes.isBlank()) return "Chưa cập nhật";
    return List.of(codes.split(",")).stream().map(ProvinceAreas::label).collect(Collectors.joining(", "));
  }
}
