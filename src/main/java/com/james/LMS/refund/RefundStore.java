package com.james.LMS.refund;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * All SQL of the refund module. Every method runs inside the caller's transaction; the locking
 * methods (lockOrder, lockEscrow, lockPayment, lockRefund, lockAdminRequest) must be called in that
 * order: order, then escrow, then payment.
 */
@Component
@RequiredArgsConstructor
public class RefundStore {

  public record OrderRow(Long id, String orderCode) {}

  public record EscrowRow(
      Long id,
      Long orderId,
      Long paymentId,
      String status,
      BigDecimal gross,
      BigDecimal platformFee,
      BigDecimal chefAmount) {}

  public record PaymentRow(Long id, Long orderId, String status, BigDecimal amount) {}

  public record AdminRequestRow(Long id, Long orderId, BigDecimal amount, String status) {}

  public record NewRefund(
      Long orderId,
      Long paymentId,
      BigDecimal amount,
      BigDecimal retained,
      int percent,
      RefundReason reason,
      Long adminRequestId) {}

  private static final String REFUND_COLUMNS =
      "id, order_id, payment_transaction_id, amount, retained_amount, policy_percent, reason_code,"
          + " status, gateway_reference, failure_message, admin_refund_request_id, created_at,"
          + " updated_at";

  private final NamedParameterJdbcTemplate jdbc;

  private static MapSqlParameterSource id(String name, Object value) {
    return new MapSqlParameterSource(name, value);
  }

  public Optional<OrderRow> lockOrder(Long orderId) {
    return jdbc
        .query(
            "SELECT id, order_code FROM orders WHERE id = :id FOR UPDATE",
            id("id", orderId),
            (row, n) -> new OrderRow(row.getLong("id"), row.getString("order_code")))
        .stream()
        .findFirst();
  }

  public Optional<EscrowRow> lockEscrow(Long orderId) {
    return jdbc
        .query(
            """
            SELECT id, order_id, payment_transaction_id, status, gross_amount, platform_fee, chef_amount
            FROM order_escrows WHERE order_id = :id FOR UPDATE
            """,
            id("id", orderId),
            (row, n) ->
                new EscrowRow(
                    row.getLong("id"),
                    row.getLong("order_id"),
                    row.getLong("payment_transaction_id"),
                    row.getString("status"),
                    row.getBigDecimal("gross_amount"),
                    row.getBigDecimal("platform_fee"),
                    row.getBigDecimal("chef_amount")))
        .stream()
        .findFirst();
  }

  public Optional<PaymentRow> lockPayment(Long paymentId) {
    return jdbc
        .query(
            "SELECT id, order_id, status, amount FROM payment_transactions WHERE id = :id FOR UPDATE",
            id("id", paymentId),
            (row, n) ->
                new PaymentRow(
                    row.getLong("id"),
                    row.getLong("order_id"),
                    row.getString("status"),
                    row.getBigDecimal("amount")))
        .stream()
        .findFirst();
  }

  /** Unlocked read, used only to learn which order to lock first. */
  public Optional<Long> paymentOrderId(Long paymentId) {
    return jdbc
        .queryForList(
            "SELECT order_id FROM payment_transactions WHERE id = :id", id("id", paymentId), Long.class)
        .stream()
        .findFirst();
  }

  public boolean hasSuccessPayment(Long orderId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM payment_transactions WHERE order_id = :id AND status = 'SUCCESS')",
            id("id", orderId),
            Boolean.class));
  }

  public Optional<Long> escrowedPaymentId(Long orderId) {
    return jdbc
        .queryForList(
            "SELECT payment_transaction_id FROM order_escrows WHERE order_id = :id",
            id("id", orderId),
            Long.class)
        .stream()
        .findFirst();
  }

  public String orderStatus(Long orderId) {
    return jdbc.queryForObject(
        "SELECT status FROM orders WHERE id = :id", id("id", orderId), String.class);
  }

  public Optional<Refund> latestForPayment(Long paymentId) {
    return jdbc
        .query(
            "SELECT " + REFUND_COLUMNS + " FROM refunds WHERE payment_transaction_id = :id ORDER BY id DESC LIMIT 1",
            id("id", paymentId),
            RefundStore::refund)
        .stream()
        .findFirst();
  }

  public Optional<Refund> byAdminRequest(Long adminRequestId) {
    return jdbc
        .query(
            "SELECT " + REFUND_COLUMNS + " FROM refunds WHERE admin_refund_request_id = :id ORDER BY id DESC LIMIT 1",
            id("id", adminRequestId),
            RefundStore::refund)
        .stream()
        .findFirst();
  }

  public Optional<Refund> lockRefund(Long refundId) {
    return jdbc
        .query(
            "SELECT " + REFUND_COLUMNS + " FROM refunds WHERE id = :id FOR UPDATE",
            id("id", refundId),
            RefundStore::refund)
        .stream()
        .findFirst();
  }

  /** Unlocked read, used only to learn which order to lock first. */
  public Optional<Refund> peek(Long refundId) {
    return jdbc
        .query(
            "SELECT " + REFUND_COLUMNS + " FROM refunds WHERE id = :id",
            id("id", refundId),
            RefundStore::refund)
        .stream()
        .findFirst();
  }

  public Refund find(Long refundId) {
    return jdbc.queryForObject(
        "SELECT " + REFUND_COLUMNS + " FROM refunds WHERE id = :id", id("id", refundId), RefundStore::refund);
  }

  public Refund insert(NewRefund refund) {
    Long newId =
        jdbc.queryForObject(
            """
            INSERT INTO refunds(order_id, payment_transaction_id, amount, retained_amount, policy_percent,
                                reason_code, status, admin_refund_request_id)
            VALUES (:order, :payment, :amount, :retained, :percent, :reason, 'REQUESTED', :admin)
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("order", refund.orderId())
                .addValue("payment", refund.paymentId())
                .addValue("amount", refund.amount())
                .addValue("retained", refund.retained())
                .addValue("percent", refund.percent())
                .addValue("reason", refund.reason().name())
                .addValue("admin", refund.adminRequestId()),
            Long.class);
    return find(newId);
  }

  public void markRefunded(Long refundId, String reference) {
    jdbc.update(
        """
        UPDATE refunds SET status = 'REFUNDED', gateway_reference = :ref, failure_message = NULL,
                           updated_at = now()
        WHERE id = :id
        """,
        id("id", refundId).addValue("ref", reference));
  }

  public void markFailed(Long refundId, String message) {
    jdbc.update(
        "UPDATE refunds SET status = 'FAILED', failure_message = :message, updated_at = now() WHERE id = :id",
        id("id", refundId).addValue("message", message));
  }

  public void markRequested(Long refundId) {
    jdbc.update(
        "UPDATE refunds SET status = 'REQUESTED', failure_message = NULL, updated_at = now() WHERE id = :id",
        id("id", refundId));
  }

  /** HELD escrow waits for the refund as REFUND_PENDING (the ledger row is written once). */
  public void holdForRefund(EscrowRow escrow, String note) {
    jdbc.update(
        "UPDATE order_escrows SET status = 'REFUND_PENDING', updated_at = now() WHERE id = :id",
        id("id", escrow.id()));
    insertLedger(escrow, "REFUND_PENDING", escrow.gross(), escrow.chefAmount(), escrow.platformFee(), note);
  }

  public void escrowRefunded(EscrowRow escrow, BigDecimal refunded, String note) {
    jdbc.update(
        "UPDATE order_escrows SET status = 'REFUNDED', updated_at = now() WHERE id = :id",
        id("id", escrow.id()));
    insertLedger(escrow, "REFUNDED", refunded, BigDecimal.ZERO, BigDecimal.ZERO, note);
  }

  private void insertLedger(
      EscrowRow escrow,
      String entry,
      BigDecimal gross,
      BigDecimal chef,
      BigDecimal fee,
      String note) {
    jdbc.update(
        """
        INSERT INTO escrow_ledger_entries(order_id, escrow_id, entry_type, gross_amount, chef_amount,
                                          platform_fee, note)
        VALUES (:order, :escrow, :entry, :gross, :chef, :fee, :note)
        ON CONFLICT (order_id, entry_type) DO NOTHING
        """,
        new MapSqlParameterSource()
            .addValue("order", escrow.orderId())
            .addValue("escrow", escrow.id())
            .addValue("entry", entry)
            .addValue("gross", gross)
            .addValue("chef", chef)
            .addValue("fee", fee)
            .addValue("note", note));
  }

  public void paymentRefunded(Long paymentId) {
    jdbc.update(
        """
        UPDATE payment_transactions SET status = 'REFUNDED', version = version + 1,
               updated_at = (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
        WHERE id = :id
        """,
        id("id", paymentId));
  }

  public Optional<AdminRequestRow> lockAdminRequest(Long requestId) {
    return jdbc
        .query(
            "SELECT id, order_id, amount, status FROM admin_refund_requests WHERE id = :id FOR UPDATE",
            id("id", requestId),
            (row, n) ->
                new AdminRequestRow(
                    row.getLong("id"),
                    row.getLong("order_id"),
                    row.getBigDecimal("amount"),
                    row.getString("status")))
        .stream()
        .findFirst();
  }

  /** Only status, gateway_reference and updated_at change; the admin module's version is left alone. */
  public void markAdminExecuted(Long requestId, String reference) {
    jdbc.update(
        """
        UPDATE admin_refund_requests SET status = 'EXECUTED', gateway_reference = :ref, updated_at = now()
        WHERE id = :id AND status = 'REVIEWED'
        """,
        id("id", requestId).addValue("ref", reference));
  }

  private static Refund refund(ResultSet row, int n) throws SQLException {
    return new Refund(
        row.getLong("id"),
        row.getLong("order_id"),
        row.getLong("payment_transaction_id"),
        row.getBigDecimal("amount"),
        row.getBigDecimal("retained_amount"),
        row.getInt("policy_percent"),
        RefundReason.valueOf(row.getString("reason_code")),
        RefundStatus.valueOf(row.getString("status")),
        row.getString("gateway_reference"),
        row.getString("failure_message"),
        (Long) row.getObject("admin_refund_request_id"),
        row.getObject("created_at", OffsetDateTime.class),
        row.getObject("updated_at", OffsetDateTime.class));
  }
}
