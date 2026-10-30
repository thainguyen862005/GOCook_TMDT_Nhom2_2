package com.james.LMS.publiccontent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** User-initiated geocoding; fail closed if provider cannot confirm BOTH province and ward.
 * OSM boundaries/names can lag 2025 reorganizations. Never pretend an unverified point is valid.
 */
@RestController
public class GeocodingController {
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper mapper;
  private final WardDirectory wards;
  private final VerifiedLocationService proofs;
  private final String baseUrl;
  private final String userAgent;
  private final Map<String,List<Map<String,Object>>> cache = new LinkedHashMap<>();
  private long lastLookupMillis = 0;
  public GeocodingController(ObjectMapper mapper, WardDirectory wards, VerifiedLocationService proofs,
      @Value("${gocook.geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
      @Value("${gocook.geocoding.user-agent:GOCook-Student-Project/2.2}") String userAgent) {
    this.mapper=mapper;this.wards=wards;this.proofs=proofs;
    this.baseUrl=baseUrl.replaceAll("/+$","");this.userAgent=userAgent;
  }

  static String normalized(String input) {
    if (input==null) return "";
    String plain=Normalizer.normalize(input.toLowerCase(Locale.ROOT).replace('đ','d'),Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+"," ").trim();
    return plain.replaceFirst("^(tinh|thanh pho|tp|city|province|phuong|xa|dac khu|ward|commune) +", "")
        .replaceFirst(" +(city|province)$", "").trim();
  }
  static boolean sameRegion(String expected,String actual) {
    return !normalized(expected).isEmpty() && normalized(expected).equals(normalized(actual));
  }
  /** Only named administrative units from the provider, not loose text in display_name. */
  static boolean matchesAdministrativeAddress(JsonNode address,String provinceName,String wardName) {
    String state=address.path("state").asText("");
    if (state.isBlank()) state=address.path("province").asText("");
    if (!sameRegion(provinceName,state)) return false;
    for (String key : List.of("suburb","quarter","neighbourhood","village","town","municipality","hamlet")) {
      if (sameRegion(wardName,address.path(key).asText(""))) return true;
    }
    return false;
  }

  @GetMapping("/api/public/geocode")
  @PreAuthorize("isAuthenticated()")
  public synchronized List<Map<String,Object>> lookup(@RequestParam String province, @RequestParam String ward,
                                                        @RequestParam String address) {
    var selectedWard=wards.require(province,ward);
    String query=address==null?"":address.trim().replaceAll("\\s+"," ");
    if (query.length()<8 || query.length()>250)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Địa chỉ phải có 8-250 ký tự.");
    String key=province+"|"+ward+"|"+query.toLowerCase(Locale.ROOT);
    if (cache.containsKey(key)) return withProof(cache.get(key),province,ward);
    try {
      long delta=System.currentTimeMillis()-lastLookupMillis;
      if (delta<1200 && lastLookupMillis>0) Thread.sleep(1200-delta);
      lastLookupMillis=System.currentTimeMillis();
      String q=query+", "+selectedWard.name()+", "+ProvinceAreas.label(province)+", Việt Nam";
      String url=baseUrl+"/search?format=jsonv2&addressdetails=1&countrycodes=vn&limit=8&q="
          +URLEncoder.encode(q,StandardCharsets.UTF_8);
      var request=HttpRequest.newBuilder(URI.create(url)).GET().header("User-Agent",userAgent)
          .header("Accept","application/json").header("Accept-Language","vi")
          .timeout(Duration.ofSeconds(9)).build();
      var response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode()!=200) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Dịch vụ địa chỉ đang bận.");
      JsonNode results=mapper.readTree(response.body());
      if (!results.isArray()) throw new IllegalStateException("Invalid geocoding response");
      var accepted=new ArrayList<Map<String,Object>>();
      for (JsonNode item:results) {
        double lat=item.path("lat").asDouble(Double.NaN),lon=item.path("lon").asDouble(Double.NaN);
        String display=item.path("display_name").asText("");
        if (!Double.isFinite(lat)||!Double.isFinite(lon)||lat<7||lat>24||lon<101||lon>115
            ||display.isBlank()||display.length()>500) continue;
        if (!matchesAdministrativeAddress(item.path("address"),ProvinceAreas.label(province),selectedWard.name())) continue;
        accepted.add(Map.of("lat",lat,"lon",lon,"display_name",display,"wardCode",ward));
      }
      if (cache.size()>=500) cache.remove(cache.keySet().iterator().next());
      cache.put(key,List.copyOf(accepted));
      return withProof(accepted,province,ward);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Tạm ngừng tìm địa chỉ.");
    } catch (ResponseStatusException error) {throw error;
    } catch (Exception error) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Không thể kiểm tra địa chỉ. Vui lòng thử lại.");
    }
  }
  private List<Map<String,Object>> withProof(List<Map<String,Object>> records,String province,String ward) {
    var out=new ArrayList<Map<String,Object>>();
    for (var r:records) {
      double lat=((Number)r.get("lat")).doubleValue(),lon=((Number)r.get("lon")).doubleValue();
      String display=(String)r.get("display_name");
      out.add(Map.of("lat",lat,"lon",lon,"display_name",display,"wardCode",ward,
          "proof",proofs.issue(province,ward,lat,lon,display)));
    }
    return out;
  }
}
