package com.james.LMS.payment.adminread;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.PaymentProvider;
import com.james.LMS.enums.PaymentStatus;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.payment.PaymentTime;
import com.james.LMS.refund.RefundProperties;
import com.james.LMS.refund.RefundStatus;
import com.james.LMS.service.PaymentGateway;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only view of payments, refunds and money that does not add up, for the admin. The DTOs
 * carry no raw gateway reference, no idempotency key and nothing signed. Time is shown as ISO text
 * in Asia/Ho_Chi_Minh.
 */
@Service
@RequiredArgsConstructor
public class PaymentAdminReadService {

  static final int MAX_PAGE_SIZE = 100;
  static final int RECONCILIATION_LIMIT = 100;

  private final NamedParameterJdbcTemplate jdbc;
  private final RefundProperties refundProperties;

  public record PaymentView(
      Long id,
      Long orderId,
      String orderCode,
      String provider,
      String txnRef,
      BigDecimal amount,
      String status,
      int attemptNo,
      String closeReason,
      String expiresAt,
      String paidAt,
      String createdAt) {}

  public record EscrowView(
      String status,
      BigDecimal grossAmount,
      BigDecimal platformFee,
      BigDecimal chefAmount,
      String heldAt,
      String releasedAt) {}

  public record RefundView(
      Long id,
      Long orderId,
      String orderCode,
      Long paymentId,
      BigDecimal amount,
      BigDecimal retainedAmount,
      int policyPercent,
      String reason,
      String status,
      String gatewayReference,
      String failureMessage,
      Long adminRefundRequestId,
      String createdAt,
      String updatedAt) {}

  public record PaymentDetail(PaymentView payment, EscrowView escrow, List<RefundView> refunds) {}

  public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

  public record UnsettledPayment(
      Long paymentId, Long orderId, String orderCode, String txnRef, BigDecimal amount,
      String orderStatus, String paidAt) {}

  public record MultiplePaid(Long orderId, String orderCode, long successCount, BigDecimal totalAmount) {}

  public record HeldWithoutPayment(
      Long escrowId, Long orderId, String orderCode, BigDecimal grossAmount, String heldAt) {}

  public record StaleRefund(
      Long escrowId, Long orderId, String orderCode, BigDecimal grossAmount, String since) {}

  public record Reconciliation(
      List<UnsettledPayment> successWithoutEscrow,
      List<MultiplePaid> ordersWithMultipleSuccess,
      List<HeldWithoutPayment> heldEscrowWithoutSuccess,
      List<StaleRefund> staleRefundPending,
      String generatedAt) {}

  private static final String PAYMENT_COLUMNS =
      """
      p.id, p.order_id, o.order_code, p.provider, p.transaction_code, p.amount, p.status, p.attempt_no,
      p.close_reason, p.expires_at, p.paid_at, p.created_at
      """;

  public Page<PaymentView> payments(
      String status,
      String provider,
      String orderCode,
      String txnRef,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    int pageSize = pageSize(page, size);
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    if (status != null) {
      where.append(" AND p.status = :status");
      params.addValue("status", enumName(PaymentStatus.class, status, "status"));
    }
    if (provider != null) {
      where.append(" AND p.provider = :provider");
      params.addValue("provider", enumName(PaymentProvider.class, provider, "provider"));
    }
    if (orderCode != null) {
      where.append(" AND o.order_code = :orderCode");
      params.addValue("orderCode", orderCode.trim());
    }
    if (txnRef != null) {
      where.append(" AND p.transaction_code = :txnRef");
      params.addValue("txnRef", txnRef.trim());
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw invalid("from không được sau to.");
    }
    if (from != null) {
      where.append(" AND p.created_at >= :fromMs");
      params.addValue("fromMs", PaymentTime.startOfDay(from).toInstant().toEpochMilli());
    }
    if (to != null) {
      where.append(" AND p.created_at < :toMs");
      params.addValue("toMs", PaymentTime.startOfDay(to.plusDays(1)).toInstant().toEpochMilli());
    }
    params.addValue("limit", pageSize).addValue("offset", (long) page * pageSize);

    long[] total = {0};
    List<PaymentView> rows =
        jdbc.query(
            "SELECT "
                + PAYMENT_COLUMNS
                + ", COUNT(*) OVER () AS total FROM payment_transactions p JOIN orders o ON o.id = p.order_id"
                + where
                + " ORDER BY p.id DESC LIMIT :limit OFFSET :offset",
            params,
            (row, n) -> {
              total[0] = row.getLong("total");
              return payment(row);
            });
    return page(rows, page, pageSize, total[0]);
  }

  public PaymentDetail payment(Long id) {
    MapSqlParameterSource byId = new MapSqlParameterSource("id", id);
    PaymentView payment =
        jdbc
            .query(
                "SELECT "
                    + PAYMENT_COLUMNS
                    + " FROM payment_transactions p JOIN orders o ON o.id = p.order_id WHERE p.id = :id",
                byId,
                (row, n) -> payment(row))
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new PaymentException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.ADMIN_PAYMENT_NOT_FOUND,
                        "Không tìm thấy giao dịch."));
    EscrowView escrow =
        jdbc
            .query(
                """
                SELECT status, gross_amount, platform_fee, chef_amount, held_at, released_at
                FROM order_escrows WHERE payment_transaction_id = :id
                """,
                byId,
                (row, n) ->
                    new EscrowView(
                        row.getString("status"),
                        row.getBigDecimal("gross_amount"),
                        row.getBigDecimal("platform_fee"),
                        row.getBigDecimal("chef_amount"),
                        PaymentTime.iso(row.getObject("held_at", OffsetDateTime.class)),
                        PaymentTime.iso(row.getObject("released_at", OffsetDateTime.class))))
            .stream()
            .findFirst()
            .orElse(null);
    List<RefundView> refunds =
        jdbc.query(
            REFUND_SELECT + " WHERE r.payment_transaction_id = :id ORDER BY r.id",
            byId,
            (row, n) -> refund(row));
    return new PaymentDetail(payment, escrow, refunds);
  }

  private static final String REFUND_SELECT =
      """
      SELECT r.id, r.order_id, o.order_code, r.payment_transaction_id, r.amount, r.retained_amount,
             r.policy_percent, r.reason_code, r.status, r.gateway_reference, r.failure_message,
             r.admin_refund_request_id, r.created_at, r.updated_at
      FROM refunds r JOIN orders o ON o.id = r.order_id
      """;

  public RefundView refund(Long id) {
    return jdbc
        .query(REFUND_SELECT + " WHERE r.id = :id", new MapSqlParameterSource("id", id), (row, n) -> refund(row))
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new PaymentException(
                    HttpStatus.NOT_FOUND, ErrorCode.REFUND_NOT_FOUND, "Không tìm thấy hoàn tiền."));
  }

  public Page<RefundView> refunds(String status, String orderCode, int page, int size) {
    int pageSize = pageSize(page, size);
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    if (status != null) {
      where.append(" AND r.status = :status");
      params.addValue("status", enumName(RefundStatus.class, status, "status"));
    }
    if (orderCode != null) {
      where.append(" AND o.order_code = :orderCode");
      params.addValue("orderCode", orderCode.trim());
    }
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM refunds r JOIN orders o ON o.id = r.order_id" + where, params, Long.class);
    params.addValue("limit", pageSize).addValue("offset", (long) page * pageSize);
    List<RefundView> rows =
        jdbc.query(
            REFUND_SELECT + where + " ORDER BY r.id DESC LIMIT :limit OFFSET :offset",
            params,
            (row, n) -> refund(row));
    return page(rows, page, pageSize, total);
  }

  public Reconciliation reconciliation() {
    MapSqlParameterSource limit = new MapSqlParameterSource("limit", RECONCILIATION_LIMIT);
    List<UnsettledPayment> unsettled =
        jdbc.query(
            """
            SELECT p.id, p.order_id, o.order_code, p.transaction_code, p.amount, o.status AS order_status,
                   p.paid_at
            FROM payment_transactions p JOIN orders o ON o.id = p.order_id
            WHERE p.status = 'SUCCESS'
              AND NOT EXISTS (SELECT 1 FROM order_escrows e WHERE e.payment_transaction_id = p.id)
            ORDER BY p.id LIMIT :limit
            """,
            limit,
            (row, n) ->
                new UnsettledPayment(
                    row.getLong("id"),
                    row.getLong("order_id"),
                    row.getString("order_code"),
                    row.getString("transaction_code"),
                    row.getBigDecimal("amount"),
                    row.getString("order_status"),
                    PaymentTime.iso(row.getObject("paid_at", OffsetDateTime.class))));
    List<MultiplePaid> multiple =
        jdbc.query(
            """
            SELECT p.order_id, o.order_code, COUNT(*) AS paid, SUM(p.amount) AS total
            FROM payment_transactions p JOIN orders o ON o.id = p.order_id
            WHERE p.status = 'SUCCESS'
            GROUP BY p.order_id, o.order_code HAVING COUNT(*) > 1
            ORDER BY p.order_id LIMIT :limit
            """,
            limit,
            (row, n) ->
                new MultiplePaid(
                    row.getLong("order_id"),
                    row.getString("order_code"),
                    row.getLong("paid"),
                    row.getBigDecimal("total")));
    List<HeldWithoutPayment> held =
        jdbc.query(
            """
            SELECT e.id, e.order_id, o.order_code, e.gross_amount, e.held_at
            FROM order_escrows e JOIN orders o ON o.id = e.order_id
            WHERE e.status = 'HELD'
              AND NOT EXISTS (SELECT 1 FROM payment_transactions p
                              WHERE p.id = e.payment_transaction_id AND p.status = 'SUCCESS')
            ORDER BY e.id LIMIT :limit
            """,
            limit,
            (row, n) ->
                new HeldWithoutPayment(
                    row.getLong("id"),
                    row.getLong("order_id"),
                    row.getString("order_code"),
                    row.getBigDecimal("gross_amount"),
                    PaymentTime.iso(row.getObject("held_at", OffsetDateTime.class))));
    List<StaleRefund> stale =
        jdbc.query(
            """
            SELECT e.id, e.order_id, o.order_code, e.gross_amount, e.updated_at
            FROM order_escrows e JOIN orders o ON o.id = e.order_id
            WHERE e.status = 'REFUND_PENDING' AND e.updated_at < :cutoff
            ORDER BY e.updated_at LIMIT :limit
            """,
            new MapSqlParameterSource("limit", RECONCILIATION_LIMIT)
                .addValue("cutoff", OffsetDateTime.now().minus(refundProperties.getPendingMaxAge())),
            (row, n) ->
                new StaleRefund(
                    row.getLong("id"),
                    row.getLong("order_id"),
                    row.getString("order_code"),
                    row.getBigDecimal("gross_amount"),
                    PaymentTime.iso(row.getObject("updated_at", OffsetDateTime.class))));
    return new Reconciliation(
        unsettled, multiple, held, stale, PaymentTime.iso(OffsetDateTime.now(PaymentGateway.GATEWAY_ZONE)));
  }

  private static PaymentView payment(ResultSet row) throws SQLException {
    return new PaymentView(
        row.getLong("id"),
        row.getLong("order_id"),
        row.getString("order_code"),
        row.getString("provider"),
        row.getString("transaction_code"),
        row.getBigDecimal("amount"),
        row.getString("status"),
        row.getInt("attempt_no"),
        row.getString("close_reason"),
        PaymentTime.iso(row.getObject("expires_at", OffsetDateTime.class)),
        PaymentTime.iso(row.getObject("paid_at", OffsetDateTime.class)),
        PaymentTime.isoMillis(row.getLong("created_at")));
  }

  private static RefundView refund(ResultSet row) throws SQLException {
    return new RefundView(
        row.getLong("id"),
        row.getLong("order_id"),
        row.getString("order_code"),
        row.getLong("payment_transaction_id"),
        row.getBigDecimal("amount"),
        row.getBigDecimal("retained_amount"),
        row.getInt("policy_percent"),
        row.getString("reason_code"),
        row.getString("status"),
        row.getString("gateway_reference"),
        row.getString("failure_message"),
        (Long) row.getObject("admin_refund_request_id"),
        PaymentTime.iso(row.getObject("created_at", OffsetDateTime.class)),
        PaymentTime.iso(row.getObject("updated_at", OffsetDateTime.class)));
  }

  private static <T> Page<T> page(List<T> content, int page, int size, long total) {
    return new Page<>(content, page, size, total, (int) ((total + size - 1) / size));
  }

  private static int pageSize(int page, int size) {
    if (page < 0 || size < 1) {
      throw invalid("page phải từ 0 và size phải từ 1.");
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }

  private static <E extends Enum<E>> String enumName(Class<E> type, String value, String label) {
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT)).name();
    } catch (IllegalArgumentException e) {
      throw invalid(label + " không hợp lệ.");
    }
  }

  private static PaymentException invalid(String message) {
    return new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.ADMIN_PAYMENT_INVALID_PARAM, message);
  }
}
