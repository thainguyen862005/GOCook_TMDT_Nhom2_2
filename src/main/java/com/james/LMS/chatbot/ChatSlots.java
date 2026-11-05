package com.james.LMS.chatbot;

import com.james.LMS.publiccontent.ProvinceAreas;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Deterministic, Vietnamese-aware slot extraction; no model retraining required. */
@Component
public class ChatSlots {
  private static final Pattern GUESTS = Pattern.compile(
      "(?<![0-9])([0-9]{1,3})\\s*(?:nguoi|nguoif|nguoeif|nguoiif|khach|suat)(?![a-z])");
  private static final Pattern ONLY_GUESTS = Pattern.compile(
      "^[0-9]{1,3}(?:\\s*(?:nguoi|nguoif|nguoeif|nguoiif|khach|suat))?$");
  private static final Pattern PRICE_WITH_UNIT = Pattern.compile(
      "([0-9]+(?:[.,][0-9]{1,3})*)\\s*(trieu|tr|nghin|ngan|k|dong|vnd|d)(?![a-z])");
  private static final Pattern PRICE_CONTEXT = Pattern.compile(
      "(?:ngan sach|budget|gia|duoi|toi da|tam|khoang|con|tren)\\s*(?:la|duoi|toi da)?\\s*([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{4,9})\\b");
  private static final Pattern MIN_PRICE = Pattern.compile(
      "(?:^|\\b)(?:tren|hon|tu|it nhat)\\s*(?:gia\\s*)?([0-9]+(?:[.,][0-9]{1,3})*)\\s*(trieu|tr|nghin|ngan|k|dong|vnd|d)?(?=\\W|$)");
  private static final String[] DISH_KEYWORDS = {
      "lẩu nấm", "phở bò", "bún bò", "chay", "hải sản", "lẩu", "nấm", "gà", "bò", "cá", "nướng", "cơm", "bún", "phở", "sushi", "tiệc", "món bắc", "món miền trung"
  };

  // budget is an upper bound: either the total amount or the price per guest
  // when perPerson is true. minUnitPrice is ALWAYS a per-guest lower bound.
  public record Values(Integer guests, Long budget, boolean perPerson, String areaCode,
      String keyword, Long minUnitPrice) {
    public Values(Integer guests, Long budget, boolean perPerson, String areaCode, String keyword) {
      this(guests, budget, perPerson, areaCode, keyword, null);
    }
  }

  public static boolean isGuestAnswer(String text) {
    return ONLY_GUESTS.matcher(normalized(text == null ? "" : text)).matches();
  }

  public static boolean isPriceFollowUp(String text) {
    String norm = normalized(text == null ? "" : text);
    boolean hasPrice = PRICE_WITH_UNIT.matcher(norm).find() || PRICE_CONTEXT.matcher(norm).find()
        || MIN_PRICE.matcher(norm).find();
    return hasPrice && (
        norm.matches("(?s).*(?:\\b(?:gia|ngan sach|tren|duoi|hon|con|giam|tang|tam|khoang|toi da|it nhat)\\b|do xuong|tro xuong|thuc don).*"));
  }

  public Values parse(String text, Values previous) {
    String norm = normalized(text == null ? "" : text.strip());
    Integer guests = previous == null ? null : previous.guests();
    Long budget = previous == null ? null : previous.budget();
    boolean perPerson = previous != null && previous.perPerson();
    String area = previous == null ? null : previous.areaCode();
    String keyword = previous == null ? null : previous.keyword();
    Long minUnitPrice = previous == null ? null : previous.minUnitPrice();

    Matcher gm = GUESTS.matcher(norm);
    if (gm.find()) {
      int parsed = Integer.parseInt(gm.group(1));
      if (parsed >= 1 && parsed <= 100) guests = parsed;
    } else if (previous != null && previous.guests() == null && norm.matches("[0-9]{1,3}")) {
      int parsed = Integer.parseInt(norm);
      if (parsed >= 1 && parsed <= 100) guests = parsed;
    }

    Matcher min = MIN_PRICE.matcher(norm);
    boolean minimumMentioned = min.find();
    if (minimumMentioned) {
      String raw = min.group(1);
      String unit = min.group(2);
      Long parsed = unit == null && previous != null && raw.matches("[0-9]{1,3}")
          ? price(raw, "k") : (unit == null ? null : price(raw, unit));
      if (parsed != null && parsed >= 1000 && parsed <= 100_000_000L) {
        minUnitPrice = parsed;
        // "Còn trên 100k" replaces an incompatible previous upper price.
        if (budget != null && (perPerson && budget <= parsed
            || !perPerson && guests != null && budget <= parsed * guests)) {
          budget = null;
          perPerson = false;
        }
      }
    } else {
      Matcher pm = PRICE_WITH_UNIT.matcher(norm);
      Long parsed = pm.find() ? price(pm.group(1), pm.group(2)) : null;
      if (parsed == null) {
        Matcher pc = PRICE_CONTEXT.matcher(norm);
        if (pc.find()) parsed = price(pc.group(1), "dong");
      }
      if (parsed != null && parsed >= 1000 && parsed <= 100_000_000L) {
        budget = parsed;
        // A menu's displayed price is VND/guest. "thực đơn ... 100k đổ xuống"
        // clearly means maximum unit price; "ngân sách 800k" means total.
        perPerson = norm.contains("moi nguoi") || norm.contains("/nguoi")
            || norm.contains("/suat") || norm.contains("dau nguoi")
            || ((norm.contains("thuc don") || norm.contains("gia"))
                && (norm.contains("do xuong") || norm.contains("tro xuong")));
        if (minUnitPrice != null && perPerson && budget <= minUnitPrice) {
          minUnitPrice = null;
        }
      } else if (previous != null && budget != null
          && (norm.equals("moi nguoi") || norm.equals("gia moi nguoi") || norm.equals("/nguoi"))) {
        // Reply to a clarification such as "100k tổng hay mỗi người?"
        perPerson = true;
        if (minUnitPrice != null && budget <= minUnitPrice) minUnitPrice = null;
      } else if (previous != null && budget != null
          && (norm.equals("tong") || norm.equals("tong cong") || norm.equals("cho ca nhom"))) {
        perPerson = false;
      }
    }

    for (String code : ProvinceAreas.codes()) {
      String label = normalized(ProvinceAreas.label(code));
      if (norm.contains(label)) { area = code; break; }
    }
    for (String candidate : DISH_KEYWORDS) {
      String token = normalized(candidate);
      boolean present = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(token) + "(?![a-z0-9])")
          .matcher(norm).find();
      if (present && (keyword == null || candidate.length() > keyword.length()
          || !norm.contains(normalized(keyword)))) keyword = candidate;
    }
    return new Values(guests, budget, perPerson, area, keyword, minUnitPrice);
  }

  public static String normalized(String input) {
    return Normalizer.normalize(input.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").trim();
  }

  static Long price(String raw, String suffix) {
    try {
      String text = raw.replace(',', '.');
      String unit = normalized(suffix == null ? "" : suffix);
      if (unit.equals("trieu") || unit.equals("tr")) return Math.round(Double.parseDouble(text) * 1_000_000);
      if (unit.equals("nghin") || unit.equals("ngan") || unit.equals("k"))
        return Math.round(Double.parseDouble(text) * 1000);
      return Long.parseLong(raw.replace(".", "").replace(",", ""));
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
