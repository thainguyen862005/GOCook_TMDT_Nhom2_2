package com.james.LMS.publiccontent;

import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Matching location without PostGIS or new database columns.
 * Customer: PROVINCE|LAT|LON; Chef: PROVINCE|LAT|LON|RADIUS_KM.
 * Coordinates are confirmed search-result points, not an assertion of road travel time.
 * Legacy province-only values are intentionally ineligible for automatic matching.
 */
public final class ServiceGeoArea {
  private ServiceGeoArea() {}
  private static final Set<Integer> RADII = Set.of(5, 10, 15, 20, 30, 40, 60);
  public record Point(String province, double lat, double lon) {}
  public record ChefZone(Point center, int radiusKm) {}

  private static Point point(String province, String latText, String lonText) {
    if (!ProvinceAreas.valid(province)) throw new IllegalArgumentException("Mã tỉnh/thành không hợp lệ");
    double lat = Double.parseDouble(latText), lon = Double.parseDouble(lonText);
    // Vietnam bounding box (including islands), a coarse sanity check only.
    if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < 7 || lat > 24 || lon < 101 || lon > 115)
      throw new IllegalArgumentException("Tọa độ nằm ngoài khu vực hỗ trợ");
    return new Point(province,lat,lon);
  }
  public static Point customer(String value) {
    try {
      var a = value.split("\\|", -1);
      if (a.length != 3 && a.length != 4) throw new IllegalArgumentException("Thiếu tọa độ");
      if (a.length == 4 && !a[3].matches("\\d{5}")) throw new IllegalArgumentException("Mã phường/xã không hợp lệ");
      return point(a[0],a[1],a[2]);
    } catch (RuntimeException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn tỉnh/thành, tìm và xác nhận vị trí nấu từ kết quả địa chỉ.");
    }
  }
  public static ChefZone chef(String value) {
    try {
      var a = value.split("\\|", -1);
      if (a.length != 4 && a.length != 5) throw new IllegalArgumentException("Thiếu phạm vi");
      if (a.length == 5 && !a[4].matches("\\d{5}")) throw new IllegalArgumentException("Mã phường/xã không hợp lệ");
      Point point = point(a[0],a[1],a[2]);
      int radius = Integer.parseInt(a[3]);
      if (!RADII.contains(radius)) throw new IllegalArgumentException("Bán kính không hợp lệ");
      return new ChefZone(point,radius);
    } catch (RuntimeException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Xác nhận vị trí xuất phát và bán kính phục vụ của Chef.");
    }
  }
  public static Optional<ChefZone> savedChef(String value) {
    try { return Optional.of(chef(value)); }
    catch (RuntimeException error) { return Optional.empty(); }
  }
  public static boolean matches(String chefValue, String requestValue) {
    try {
      // Old coordinates have no province/ward verification. Do not match them automatically.
      if (requestValue == null || chefValue == null
          || requestValue.split("\\|",-1).length != 4 || chefValue.split("\\|",-1).length != 5) return false;
      Point customer = customer(requestValue);
      return savedChef(chefValue).filter(c -> distanceKm(c.center,customer) <= c.radiusKm).isPresent();
    } catch (RuntimeException error) { return false; }
  }
  public static double distanceKm(Point a, Point b) {
    double lat = Math.toRadians(b.lat - a.lat), lon = Math.toRadians(b.lon - a.lon);
    double h = Math.pow(Math.sin(lat/2),2) + Math.cos(Math.toRadians(a.lat))
        * Math.cos(Math.toRadians(b.lat)) * Math.pow(Math.sin(lon/2),2);
    return 6371.0088 * 2 * Math.atan2(Math.sqrt(Math.max(0,h)),Math.sqrt(Math.max(0,1-h)));
  }
  public static String province(String token) { return token == null ? "" : token.split("\\|",-1)[0]; }
}
