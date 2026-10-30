package com.james.LMS.publiccontent;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Short-lived opaque proofs for server-verified geocoding results.
 * A raw client coordinate is NEVER enough to save a new matching location.
 * Single-instance deployment only. Use a shared token store for multiple backend replicas.
 */
@Service
public class VerifiedLocationService {
  private static final long TTL_MS = Duration.ofMinutes(20).toMillis();
  private final Map<String, Proof> issued = new HashMap<>();
  private final SecureRandom random = new SecureRandom();
  private final WardDirectory wards;
  private record Proof(String province, String ward, double lat, double lon, String address, long expires) {}

  public VerifiedLocationService(WardDirectory wards) { this.wards = wards; }
  public synchronized String issue(String province, String ward, double lat, double lon, String address) {
    wards.require(province, ward);
    if (!Double.isFinite(lat) || !Double.isFinite(lon) || address == null || address.isBlank())
      throw new IllegalArgumentException("Invalid geocoding point");
    if (issued.size() > 3000) issued.entrySet().removeIf(e -> e.getValue().expires < System.currentTimeMillis());
    if (issued.size() > 3500) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Quá nhiều vị trí đang xác nhận.");
    byte[] bytes = new byte[24]; random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    issued.put(token, new Proof(province,ward,lat,lon,address,System.currentTimeMillis()+TTL_MS));
    return token;
  }
  public synchronized String confirmCustomer(String input, String enteredAddress) {
    String[] a = split(input, 5);
    check(a[0],a[3],a[1],a[2],a[4],enteredAddress);
    return String.join("|", a[0],a[1],a[2],a[3]);
  }
  public synchronized String confirmChef(String input) {
    String[] a = split(input, 6);
    ServiceGeoArea.chef(String.join("|",a[0],a[1],a[2],a[3],a[4]));
    check(a[0],a[4],a[1],a[2],a[5],null);
    return String.join("|",a[0],a[1],a[2],a[3],a[4]);
  }
  private String[] split(String input,int count) {
    String[] a = input == null ? new String[0] : input.split("\\|", -1);
    if (a.length != count) throw rejected();
    return a;
  }
  private ResponseStatusException rejected() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hãy tìm và xác nhận lại địa chỉ theo tỉnh/phường đã chọn.");
  }
  private void check(String province, String ward, String latText, String lonText, String token, String enteredAddress) {
    wards.require(province,ward);
    Proof p=issued.get(token);
    if (p==null || p.expires < System.currentTimeMillis() || !p.province.equals(province) || !p.ward.equals(ward))
      throw rejected();
    try {
      if (Math.abs(Double.parseDouble(latText)-p.lat)>0.000011 ||
          Math.abs(Double.parseDouble(lonText)-p.lon)>0.000011) throw rejected();
    } catch (NumberFormatException error) { throw rejected(); }
    if (enteredAddress!=null && !enteredAddress.strip().equals(p.address.strip()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Địa chỉ đã thay đổi sau khi xác nhận. Vui lòng tìm lại địa chỉ.");
  }
}
