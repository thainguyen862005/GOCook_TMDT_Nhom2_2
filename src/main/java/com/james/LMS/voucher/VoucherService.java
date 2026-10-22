package com.james.LMS.voucher;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Voucher rules and the voucher counter. admin_vouchers belongs to the admin module and is only
 * read, except used_count which is moved here under a row lock.
 */
@Service
@RequiredArgsConstructor
public class VoucherService {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private static final String SELECT_VOUCHER =
      """
      SELECT id, code, discount_type, discount_value, min_order_amount, max_discount,
             usage_limit, used_count, starts_at, ends_at, is_active
      FROM admin_vouchers WHERE code = :code
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final VoucherProperties properties;

  record Voucher(
      Long id,
      String code,
      String discountType,
      BigDecimal discountValue,
      BigDecimal minOrderAmount,
      BigDecimal maxDiscount,
      int usageLimit,
      int usedCount,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      boolean active) {}

  /** Checks every rule and computes the discount without reserving anything. */
  public VoucherQuote validateAndCalculate(
      String code, Long customerId, BigDecimal subtotal, OffsetDateTime now) {
    Voucher voucher = find(code, false);
    return quote(voucher, customerId, subtotal, now);
  }

  /**
   * Reserves the voucher for an order, inside the caller's transaction: locks the voucher row,
   * checks every rule again, counts the use and records the redemption. The discount must be the
   * one the caller already priced the order with.
   */
  public void reserve(
      Long orderId,
      Long customerId,
      BigDecimal subtotal,
      VoucherQuote priced,
      OffsetDateTime now) {
    Voucher voucher = find(priced.code(), true);
    VoucherQuote locked = quote(voucher, customerId, subtotal, now);
    if (locked.discountAmount().compareTo(priced.discountAmount()) != 0) {
      throw rule(
          HttpStatus.CONFLICT,
          ErrorCode.VOUCHER_CHANGED,
          "Mã ưu đãi vừa thay đổi. Vui lòng thử lại.");
    }
    jdbc.update(
        "UPDATE admin_vouchers SET used_count = used_count + 1 WHERE id = :id",
        new MapSqlParameterSource("id", voucher.id()));
    jdbc.update(
        """
        INSERT INTO voucher_redemptions(voucher_id, order_id, customer_id, discount_amount, status)
        VALUES (:voucher, :order, :customer, :discount, 'RESERVED')
        """,
        new MapSqlParameterSource()
            .addValue("voucher", voucher.id())
            .addValue("order", orderId)
            .addValue("customer", customerId)
            .addValue("discount", locked.discountAmount()));
  }

  /** The order was paid and settled: the reservation becomes final. Safe to repeat. */
  public void consume(Long orderId) {
    jdbc.update(
        """
        UPDATE voucher_redemptions SET status = 'CONSUMED', updated_at = now()
        WHERE order_id = :order AND status = 'RESERVED'
        """,
        new MapSqlParameterSource("order", orderId));
  }

  /** The order ended unpaid: give the use back (never below zero). Safe to repeat. */
  public void release(Long orderId) {
    jdbc.update(
        """
        WITH released AS (
          UPDATE voucher_redemptions SET status = 'RELEASED', updated_at = now()
          WHERE order_id = :order AND status = 'RESERVED'
          RETURNING voucher_id)
        UPDATE admin_vouchers v SET used_count = GREATEST(v.used_count - 1, 0)
        FROM released WHERE v.id = released.voucher_id
        """,
        new MapSqlParameterSource("order", orderId));
  }

  /**
   * The whole order was refunded: give the use back even if it was already consumed (release only
   * undoes a reservation). Never below zero. Safe to repeat.
   */
  public void returnAfterRefund(Long orderId) {
    jdbc.update(
        """
        WITH returned AS (
          UPDATE voucher_redemptions SET status = 'RELEASED', updated_at = now()
          WHERE order_id = :order AND status IN ('RESERVED', 'CONSUMED')
          RETURNING voucher_id)
        UPDATE admin_vouchers v SET used_count = GREATEST(v.used_count - 1, 0)
        FROM returned WHERE v.id = returned.voucher_id
        """,
        new MapSqlParameterSource("order", orderId));
  }

  Voucher find(String rawCode, boolean lock) {
    String code = rawCode.trim().toUpperCase(Locale.ROOT);
    List<Voucher> found =
        jdbc.query(
            SELECT_VOUCHER + (lock ? " FOR UPDATE" : ""),
            new MapSqlParameterSource("code", code),
            (row, number) -> voucher(row));
    if (found.isEmpty()) {
      throw rule(HttpStatus.NOT_FOUND, ErrorCode.VOUCHER_NOT_FOUND, "Mã ưu đãi không tồn tại.");
    }
    return found.get(0);
  }

  private static Voucher voucher(ResultSet row) throws SQLException {
    return new Voucher(
        row.getLong("id"),
        row.getString("code"),
        row.getString("discount_type"),
        row.getBigDecimal("discount_value"),
        row.getBigDecimal("min_order_amount"),
        row.getBigDecimal("max_discount"),
        row.getInt("usage_limit"),
        row.getInt("used_count"),
        row.getObject("starts_at", OffsetDateTime.class),
        row.getObject("ends_at", OffsetDateTime.class),
        row.getBoolean("is_active"));
  }

  private VoucherQuote quote(
      Voucher voucher, Long customerId, BigDecimal subtotal, OffsetDateTime now) {
    if (!voucher.active()) {
      throw rule(HttpStatus.BAD_REQUEST, ErrorCode.VOUCHER_INACTIVE, "Mã ưu đãi hiện không hoạt động.");
    }
    if (now.isBefore(voucher.startsAt())) {
      throw rule(HttpStatus.BAD_REQUEST, ErrorCode.VOUCHER_NOT_STARTED, "Mã ưu đãi chưa đến ngày áp dụng.");
    }
    if (!now.isBefore(voucher.endsAt())) {
      throw rule(HttpStatus.BAD_REQUEST, ErrorCode.VOUCHER_EXPIRED, "Mã ưu đãi đã hết hạn.");
    }
    if (subtotal.compareTo(voucher.minOrderAmount()) < 0) {
      throw rule(
          HttpStatus.BAD_REQUEST,
          ErrorCode.VOUCHER_MIN_ORDER,
          "Đơn hàng chưa đạt giá trị tối thiểu để dùng mã này.");
    }
    if (voucher.usedCount() >= voucher.usageLimit()) {
      throw rule(HttpStatus.CONFLICT, ErrorCode.VOUCHER_EXHAUSTED, "Mã ưu đãi đã hết lượt sử dụng.");
    }
    if (customerRedemptions(voucher.id(), customerId) >= properties.getPerCustomerLimit()) {
      throw rule(
          HttpStatus.CONFLICT,
          ErrorCode.VOUCHER_CUSTOMER_LIMIT,
          "Bạn đã dùng hết số lần cho phép của mã này.");
    }

    return new VoucherQuote(voucher.id(), voucher.code(), discount(voucher, subtotal));
  }

  private static BigDecimal discount(Voucher voucher, BigDecimal subtotal) {
    BigDecimal discount =
        "PERCENT".equals(voucher.discountType())
            ? subtotal.multiply(voucher.discountValue()).divide(HUNDRED, 0, RoundingMode.DOWN)
            : voucher.discountValue().setScale(0, RoundingMode.DOWN);
    if (voucher.maxDiscount() != null) {
      discount = discount.min(voucher.maxDiscount().setScale(0, RoundingMode.DOWN));
    }
    return discount.min(subtotal);
  }

  int customerRedemptions(Long voucherId, Long customerId) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM voucher_redemptions
            WHERE voucher_id = :voucher AND customer_id = :customer
              AND status IN ('RESERVED', 'CONSUMED')
            """,
            new MapSqlParameterSource().addValue("voucher", voucherId).addValue("customer", customerId),
            Integer.class);
    return count == null ? 0 : count;
  }

  private static PaymentException rule(HttpStatus status, ErrorCode code, String message) {
    return new PaymentException(status, code, message);
  }
}
