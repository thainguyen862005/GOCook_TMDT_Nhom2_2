package com.james.LMS.seller.orders;

import java.util.Set;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Centralized, server-side evidence rules, independent of the UI. */
public final class OrderEvidencePolicy {
  private OrderEvidencePolicy() {}
  public static final String INGREDIENT_RECEIPT="INGREDIENT_RECEIPT";
  public static final String FINISHED_MEAL="FINISHED_MEAL";

  private static final Pattern PURCHASE_DATE = Pattern.compile("(?<!\\d)\\d{1,2}/\\d{1,2}/\\d{4}(?!\\d)");
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d/M/uuuu")
      .withResolverStyle(ResolverStyle.STRICT);

  /** Receipt notes must state merchant, calendar date and ingredient summary. Not proof of authenticity. */
  public static void validateReceiptNote(String note) {
    String value = note == null ? "" : note.strip();
    var matcher = PURCHASE_DATE.matcher(value);
    if (!matcher.find() || matcher.start() < 4 || value.length()-matcher.end() < 4)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Ghi tên cửa hàng, ngày mua (dd/MM/yyyy) và nguyên liệu; ví dụ: Co.opmart, 29/10/2026, rau củ.");
    try {
      LocalDate purchased = LocalDate.parse(matcher.group(), DATE_FORMAT);
      if (purchased.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ngày mua không được ở tương lai.");
    } catch (DateTimeParseException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ngày mua không hợp lệ.");
    }
  }

  public static String validateType(String raw) {
    String value = raw == null ? INGREDIENT_RECEIPT : raw;
    if (!Set.of(INGREDIENT_RECEIPT, FINISHED_MEAL).contains(value))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Loại bằng chứng không hợp lệ.");
    return value;
  }

  public static void validateUploadStage(String status, String type) {
    if (INGREDIENT_RECEIPT.equals(type) && !Set.of("PREPARING","COOKING").contains(status))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ được tải hóa đơn khi đang chuẩn bị hoặc nấu.");
    if (FINISHED_MEAL.equals(type) && !"COOKING".equals(status))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Chỉ tải ảnh món hoàn thành khi đang nấu.");
  }

  public static void validateRemovalStage(String status, String type) {
    if (INGREDIENT_RECEIPT.equals(type) && !"PREPARING".equals(status))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Hóa đơn đã dùng để bắt đầu nấu không thể xóa. Liên hệ hỗ trợ nếu cần điều chỉnh.");
    if (FINISHED_MEAL.equals(type) && !"COOKING".equals(status))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Ảnh nghiệm thu đã được gửi tới khách, không thể xóa.");
  }

  public static void requireBeforeTransition(String expected, String target, long receiptCount, long photoCount) {
    if ("PREPARING".equals(expected) && "COOKING".equals(target) && receiptCount == 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Cần tải ít nhất một ảnh hóa đơn nguyên liệu trước khi bắt đầu nấu.");
    if ("COOKING".equals(expected) && "AWAITING_ACCEPTANCE".equals(target) && photoCount == 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Cần tải ít nhất một ảnh món hoàn thành trước khi gửi khách nghiệm thu.");
  }
}
