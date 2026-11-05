package com.james.LMS.chatbot;

import com.james.LMS.publiccontent.PublicChefService;
import com.james.LMS.publiccontent.ProvinceAreas;
import com.james.LMS.recommendation.RecommendationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, grounded chatbot actions. Not authorized to create bookings, payments or tickets. */
@Service
public class ChatbotService {
  public record Context(String lastIntent, ChatSlots.Values slots) {}
  public record ChatRequest(String message, Context context) {}
  public record MenuCard(long id, String name, String category, String unitPrice,
      int minGuests, int maxGuests, String imageUrl, String sellerName, String reason) {}
  public record ChefCard(long id, String displayName, String serviceArea,
      String fromPrice, double rating, String specialty) {}
  public record Reply(String intent, double confidence, String message,
      List<MenuCard> menus, List<ChefCard> chefs, String helpUrl, Context context) {}

  private final IntentClassifier classifier;
  private final ChatSlots slotParser;
  private final NamedParameterJdbcTemplate jdbc;
  private final PublicChefService chefs;
  private final RecommendationService recommendations;

  public ChatbotService(IntentClassifier classifier, ChatSlots slotParser,
      NamedParameterJdbcTemplate jdbc, PublicChefService chefs, RecommendationService recommendations) {
    this.classifier = classifier;
    this.slotParser = slotParser;
    this.jdbc = jdbc;
    this.chefs = chefs;
    this.recommendations = recommendations;
  }

  @Transactional(readOnly = true)
  public Reply answer(String input, Context previous, Long customerId) {
    String text = input == null ? "" : input.strip();
    IntentClassifier.Prediction prediction = classifier.predict(text);
    String intent = prediction.intent();
    String norm = ChatSlots.normalized(text);

    // Strong task wording wins over a low-confidence classifier result. These
    // cues are restricted to *search* requests, not generic FAQ/booking text.
    boolean explicitlySearchingChef = mentionsChefSearch(norm);
    boolean explicitlySearchingMenu = !explicitlySearchingChef && mentionsMenuSearch(norm);
    boolean explicitRefundPolicy = (norm.contains("refund") || norm.contains("hoan tien"))
        && (norm.contains("chinh sach") || norm.contains("quy dinh"));
    boolean previousMenuSearch = previous != null && Set.of("SEARCH_MENU", "RECOMMEND_MENU")
        .contains(previous.lastIntent());
    boolean previousChefSearch = previous != null && "SEARCH_CHEF".equals(previous.lastIntent());
    boolean guestsOnly = previousMenuSearch && previous.slots() != null
        && previous.slots().guests() == null && ChatSlots.isGuestAnswer(text);
    boolean priceFollowUp = previousMenuSearch && ChatSlots.isPriceFollowUp(text);
    boolean unitClarification = previousMenuSearch && previous.slots() != null
        && previous.slots().budget() != null && Set.of(
        "moi nguoi", "gia moi nguoi", "/nguoi", "tong", "tong cong", "cho ca nhom")
        .contains(norm);

    if (explicitlySearchingChef) {
      intent = "SEARCH_CHEF";
    } else if (explicitlySearchingMenu) {
      intent = "SEARCH_MENU";
    } else if (explicitRefundPolicy) {
      intent = "FAQ";
    } else if (guestsOnly || priceFollowUp || unitClarification) {
      // "3 nguoeif", "giảm xuống còn 100k", "còn trên 100k" etc.
      intent = previous.lastIntent();
    } else if (previousChefSearch && (norm.startsWith("o ") || norm.startsWith("tai "))
        && norm.length() <= 80) {
      intent = "SEARCH_CHEF";
    } else if ("OUT_OF_SCOPE".equals(intent) || "UNKNOWN".equals(intent)
        || prediction.confidence() < 0.24 || prediction.margin() < 0.035) {
      intent = "FALLBACK";
    }

    // Inherit filters for follow-ups about the SAME goal only.
    ChatSlots.Values prior = previous != null && intent.equals(previous.lastIntent())
        && Set.of("SEARCH_MENU", "SEARCH_CHEF", "RECOMMEND_MENU").contains(intent)
        ? previous.slots() : null;
    ChatSlots.Values slots = slotParser.parse(text, prior);
    if (slots.areaCode() != null && !ProvinceAreas.valid(slots.areaCode())) {
      slots = new ChatSlots.Values(slots.guests(), slots.budget(), slots.perPerson(),
          null, slots.keyword(), slots.minUnitPrice());
    }
    if (slots.guests() != null && (slots.guests() < 1 || slots.guests() > 100)) {
      slots = new ChatSlots.Values(null, slots.budget(), slots.perPerson(),
          slots.areaCode(), slots.keyword(), slots.minUnitPrice());
    }
    if (slots.budget() != null && (slots.budget() < 1000 || slots.budget() > 100_000_000L)) {
      slots = new ChatSlots.Values(slots.guests(), null, false,
          slots.areaCode(), slots.keyword(), slots.minUnitPrice());
    }
    Context next = new Context(intent, slots);
    double confidence = Math.round(prediction.confidence() * 1000.0) / 1000.0;

    // "Giảm còn 100k" after a total-budget search is ambiguous because cards
    // show prices per guest. Ask instead of guessing and silently filtering wrong.
    if (previousMenuSearch && priceFollowUp && slots.budget() != null
        && slots.guests() != null && !slots.perPerson()
        && norm.contains("giam") && norm.contains("con")
        && !norm.contains("tong") && !norm.contains("ngan sach")) {
      return reply(intent, confidence,
          "Bạn muốn giới hạn " + vnd(slots.budget()) + " cho cả " + slots.guests()
              + " người, hay " + vnd(slots.budget())
              + "/người? Bạn trả lời 'tổng' hoặc 'mỗi người' nhé.", next);
    }

    return switch (intent) {
      case "GREETING" -> reply(intent, confidence,
          "Xin chào! Mình có thể giúp tìm thực đơn, đầu bếp, giải đáp về đặt lịch, giỏ hàng và thanh toán. Bạn cần gì ạ?", next);
      case "SEARCH_MENU" -> findMenus(slots, confidence, next, false, customerId);
      case "RECOMMEND_MENU" -> findMenus(slots, confidence, next, true, customerId);
      case "SEARCH_CHEF" -> findChefs(slots, confidence, next);
      case "FAQ" -> faq(text, confidence, next);
      case "CART_HELP" -> reply(intent, confidence,
          "Bạn có thể thêm thực đơn vào giỏ từ trang chi tiết thực đơn và xem lại ở /cart. Cần đăng nhập để quản lý giỏ. Khi đặt, hệ thống xử lý thanh toán theo từng thực đơn. Mở trang Giỏ hàng để thao tác nhé.", next);
      case "BOOKING_HELP" -> reply(intent, confidence,
          "Bạn có thể mở trang Thực đơn để chọn món/đầu bếp, hoặc trang Yêu cầu để gửi nhu cầu nấu ăn riêng. Hãy điền số khách, ngày giờ và địa điểm rồi làm theo các bước đặt dịch vụ.", next);
      case "PAYMENT_HELP" -> reply(intent, confidence,
          "GO Cook hỗ trợ thanh toán qua quy trình checkout và VNPay. Nếu giao dịch bị trừ tiền nhưng trạng thái chưa cập nhật, bạn nên kiểm tra chi tiết đơn và liên hệ Admin tại trang Hỗ trợ; chatbot không thể xác nhận giao dịch thay hệ thống.", next);
      case "ORDER_HELP" -> reply(intent, confidence, customerId != null
          ? "Bạn có thể kiểm tra tình trạng đơn và lịch sử mua tại trang /orders. Chatbot không trực tiếp thay đổi hoặc hủy đơn."
          : "Hãy đăng nhập tài khoản Customer để xem đơn hàng của mình, sau đó mở /orders. Chatbot không truy cập đơn hàng riêng tư của người khác.", next);
      case "CONTACT_ADMIN" -> new Reply(intent, confidence,
          "Bạn có thể liên hệ Admin tại Trung tâm trợ giúp (/help). Khi đăng nhập, bạn có thể gửi phiếu yêu cầu hỗ trợ để được xử lý.", List.of(), List.of(), "/help", next);
      default -> fallback(confidence, previous == null ? next : previous);
    };
  }

  private static boolean mentionsChefSearch(String normalized) {
    boolean refersToChef = normalized.contains("dau bep") || normalized.contains("chef");
    if (!refersToChef) return false;
    if (normalized.contains("lam sao") || normalized.contains("chinh sach")
        || normalized.contains("cach dat") || normalized.contains("quy dinh")) return false;
    return normalized.contains("tim ") || normalized.contains("danh sach")
        || normalized.contains("liet ke") || normalized.contains("gioi thieu")
        || normalized.contains("co dau bep") || normalized.contains("co chef");
  }

  private static boolean mentionsMenuSearch(String normalized) {
    boolean menu = normalized.contains("thuc don") || normalized.contains("menu")
        || normalized.contains("tim mon") || normalized.contains("cac mon");
    return menu && !normalized.contains("lam sao") && !normalized.contains("gio hang")
        && !normalized.contains("chinh sach") && !normalized.contains("tuong tu")
        && !normalized.contains("goi y") && (normalized.contains("tim ")
            || normalized.contains("cho toi") || normalized.contains("cho minh")
            || normalized.contains("xem ") || normalized.contains("liet ke")
            || normalized.contains("danh sach"));
  }

  private static String vnd(long value) {
    return String.format("%,d", value).replace(',', '.') + "đ";
  }

  private Reply findMenus(ChatSlots.Values slots, double confidence, Context context,
      boolean recommendation, Long customerId) {
    Integer guests = slots.guests();
    Long budget = slots.budget();
    if (budget != null && guests == null && !slots.perPerson()) {
      return reply(context.lastIntent(), confidence,
          "Bạn dự định đặt cho bao nhiêu người? Mình cần số khách để so sánh tổng ngân sách với giá thực đơn theo đầu người.", context);
    }
    List<MenuCard> found = new ArrayList<>();
    boolean fromKnn = false;
    if (recommendation && customerId != null && slots.keyword() == null) {
      try {
        for (var menu : recommendations.recommend(customerId, 8, 5)) {
          if (acceptable(new BigDecimal(menu.unitPrice()), menu.minGuests(), menu.maxGuests(), guests, budget, slots.perPerson(), slots.minUnitPrice())) {
            found.add(new MenuCard(menu.id(), menu.name(), menu.category(), menu.unitPrice(),
                menu.minGuests(), menu.maxGuests(), menu.imageUrl(), menu.sellerName(),
                "Tương đồng nội dung với thực đơn trong lịch sử mua hoặc giỏ hàng"));
          }
          if (found.size() >= 4) break;
        }
      } catch (RuntimeException ex) {
        // KNN is a supporting system; catalog search remains available.
      }
      fromKnn = !found.isEmpty();
    }
    if (recommendation && found.isEmpty() && slots.keyword() != null) {
      List<MenuCard> seeds = searchCatalog(slots, 1);
      if (!seeds.isEmpty()) {
        try {
          for (var m : recommendations.similar(seeds.get(0).id(), 6)) {
            if (acceptable(new BigDecimal(m.unitPrice()), m.minGuests(), m.maxGuests(), guests, budget, slots.perPerson(), slots.minUnitPrice())) {
              found.add(new MenuCard(m.id(), m.name(), m.category(), m.unitPrice(),
                  m.minGuests(), m.maxGuests(), m.imageUrl(), m.sellerName(),
                  "Tương tự món " + seeds.get(0).name()));
            }
            if (found.size() >= 4) break;
          }
        } catch (RuntimeException ex) { /* Use catalog fallback. */ }
      }
      fromKnn = !found.isEmpty();
    }
    if (found.isEmpty()) found = searchCatalog(slots, 4);
    if (found.isEmpty()) return new Reply(context.lastIntent(), confidence,
        "Mình chưa tìm thấy thực đơn công khai phù hợp các điều kiện này. Bạn có thể đổi ngân sách, số người hoặc gửi yêu cầu cho Admin.",
        List.of(), List.of(), "/help", context);
    String note = recommendation
        ? (fromKnn ? "Đây là các thực đơn gợi ý bằng KNN từ dữ liệu hiện có."
           : "Chưa đủ tín hiệu KNN để cá nhân hóa; dưới đây là vài thực đơn đang mở bán để bạn tham khảo.")
        : "Mình tìm được các thực đơn đang mở bán phù hợp để bạn xem thêm.";
    if (slots.minUnitPrice() != null) {
      note += " Đã lọc thực đơn giá trên " + String.format("%,d", slots.minUnitPrice()).replace(',', '.') + "đ/người.";
    }
    if (slots.areaCode() != null) note += " Khu vực phục vụ cần xác nhận lại với Chef trước khi đặt.";
    return new Reply(context.lastIntent(), confidence, note,
        List.copyOf(found), List.of(), null, context);
  }

  private List<MenuCard> searchCatalog(ChatSlots.Values slots, int limit) {
    String term = slots.keyword() == null ? "" : slots.keyword();
    int guests = slots.guests() == null ? 0 : slots.guests();
    long budget = slots.budget() == null ? 0 : slots.budget();
    String area = slots.areaCode() == null ? "" : slots.areaCode();
    String sql = """
        SELECT p.id, p.name, p.category, p.unit_price AS "unitPrice",
               p.min_guests AS "minGuests", p.max_guests AS "maxGuests",
               p.image_url AS "imageUrl", COALESCE(u.username,'') AS "sellerName"
        FROM products p JOIN users u ON u.id=p.seller_id
        JOIN chef_profiles cp ON cp.user_id=p.seller_id
        WHERE p.is_active=TRUE AND p.status='PUBLISHED' AND u.is_active=TRUE
          AND cp.is_active=TRUE AND cp.is_verified=TRUE AND cp.verification_status='APPROVED'
          AND (:term='' OR p.name ILIKE :query ESCAPE '\\'
             OR COALESCE(p.category,'') ILIKE :query ESCAPE '\\'
             OR COALESCE(p.dish_items,'') ILIKE :query ESCAPE '\\'
             OR COALESCE(p.description,'') ILIKE :query ESCAPE '\\')
          AND (:guests=0 OR (p.min_guests <= :guests AND p.max_guests >= :guests))
          AND (:budget=0 OR (:person=TRUE AND p.unit_price <= :budget)
             OR (:person=FALSE AND :guests>0 AND p.unit_price * :guests <= :budget))
          AND (:minUnitPrice=0 OR p.unit_price > :minUnitPrice)
          AND (:area='' OR :area = ANY(string_to_array(split_part(COALESCE(cp.service_area,''),'|',1),',')))
        ORDER BY p.updated_at DESC, p.id DESC LIMIT :size
        """;
    String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    var params = new MapSqlParameterSource().addValue("term", term).addValue("query", "%"+escaped+"%")
        .addValue("guests", guests).addValue("budget", budget).addValue("person", slots.perPerson())
        .addValue("minUnitPrice", slots.minUnitPrice() == null ? 0L : slots.minUnitPrice())
        .addValue("area", area).addValue("size", limit);
    return jdbc.query(sql, params, (rs, index) -> new MenuCard(
        rs.getLong("id"), rs.getString("name"), rs.getString("category"),
        rs.getBigDecimal("unitPrice").toPlainString(), rs.getInt("minGuests"),
        rs.getInt("maxGuests"), rs.getString("imageUrl"), rs.getString("sellerName"),
        "Đang mở bán trên GO Cook"));
  }

  private static boolean acceptable(BigDecimal unitPrice, int minGuests, int maxGuests,
      Integer guests, Long budget, boolean perPerson, Long minUnitPrice) {
    if (guests != null && (guests < minGuests || guests > maxGuests)) return false;
    if (minUnitPrice != null && unitPrice.compareTo(BigDecimal.valueOf(minUnitPrice)) <= 0) return false;
    if (budget == null) return true;
    if (perPerson) return unitPrice.compareTo(BigDecimal.valueOf(budget)) <= 0;
    return guests != null && unitPrice.multiply(BigDecimal.valueOf(guests))
        .compareTo(BigDecimal.valueOf(budget)) <= 0;
  }

  private Reply findChefs(ChatSlots.Values slots, double confidence, Context context) {
    String area = slots.areaCode() == null ? "" : slots.areaCode();
    String category = slots.keyword() == null ? "" : "%" + slots.keyword() + "%";
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> rows = (List<Map<String,Object>>) chefs.search("", area, category, 0, 4, "rating").get("content");
    List<ChefCard> result = new ArrayList<>();
    for (Map<String,Object> row : rows) {
      Number n = (Number)row.get("id");
      String price = row.get("fromPrice") == null ? "" : row.get("fromPrice").toString();
      Number rating = row.get("rating") instanceof Number r ? r : 0;
      result.add(new ChefCard(n.longValue(), String.valueOf(row.get("displayName")),
          String.valueOf(row.get("serviceArea")), price, rating.doubleValue(),
          row.get("specialties") == null ? "" : row.get("specialties").toString()));
    }
    return new Reply(context.lastIntent(), confidence,
        result.isEmpty()
            ? "Chưa có đầu bếp đã duyệt phù hợp khu vực/chuyên môn này. Bạn có thể đổi điều kiện hoặc liên hệ Admin."
            : "Mình tìm được các Chef đã xác minh theo khu vực/chuyên môn (nếu có tiêu chí). Giá, số khách và ngày giờ phục vụ cần xem thực đơn hoặc xác nhận trực tiếp với Chef.",
        List.of(), List.copyOf(result), result.isEmpty()?"/help":null, context);
  }

  private Reply faq(String question, double confidence, Context context) {
    List<Map<String,Object>> rows = jdbc.queryForList("""
        SELECT question, answer FROM public_faqs WHERE is_active=TRUE
        ORDER BY display_order ASC,id ASC LIMIT 100
        """, new MapSqlParameterSource());
    String q = faqTerms(question);
    boolean asksRefund = q.contains("hoan tien");
    double best = 0;
    String answer = null;
    for (Map<String,Object> item : rows) {
      String candidate = faqTerms(String.valueOf(item.get("question")));
      // Never answer a refund-policy question with an unrelated generic FAQ.
      if (asksRefund && !candidate.contains("hoan tien")) continue;
      double similarity = overlap(q, candidate);
      if (similarity > best) { best = similarity; answer = String.valueOf(item.get("answer")); }
    }
    // Only answer approved FAQ entries. No invented GO Cook terms or policies.
    if (answer != null && best >= 0.30) return reply("FAQ", confidence, answer, context);
    return new Reply("FAQ", confidence,
        "Mình chưa tìm thấy câu trả lời đủ chắc chắn trong FAQ đã được Admin duyệt. Bạn xem trang Hỗ trợ hoặc gửi yêu cầu cho Admin nhé.",
        List.of(), List.of(), "/help", context);
  }

  private static String faqTerms(String value) {
    String n = ChatSlots.normalized(value).replaceAll("\\brefund\\b", "hoan tien");
    return n.replaceAll("\\b(?:ban|toi|minh|co|the|noi|ve|giup|cho|duoc|khong|hoi|la|nhu|the|nao|o|go|cook)\\b", " ")
        .replaceAll("\\s+", " ").trim();
  }

  private static double overlap(String a, String b) {
    Set<String> aa = new HashSet<>(List.of(a.split(" ")));
    Set<String> bb = new HashSet<>(List.of(b.split(" ")));
    aa.remove(""); bb.remove("");
    if (aa.isEmpty() || bb.isEmpty()) return 0;
    Set<String> inter = new HashSet<>(aa); inter.retainAll(bb);
    Set<String> union = new HashSet<>(aa); union.addAll(bb);
    return (double)inter.size()/union.size();
  }

  private Reply fallback(double confidence, Context context) {
    return new Reply("FALLBACK", confidence,
        "Mình chưa hiểu rõ hoặc chưa có thông tin đáng tin cậy để trả lời. Bạn có thể hỏi lại cụ thể hơn, hoặc liên hệ Admin ở trang Hỗ trợ.",
        List.of(), List.of(), "/help", context);
  }

  private Reply reply(String intent, double confidence, String text, Context context) {
    return new Reply(intent, confidence, text, List.of(), List.of(), null, context);
  }
}
