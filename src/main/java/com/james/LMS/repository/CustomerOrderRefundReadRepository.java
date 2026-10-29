package com.james.LMS.repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CustomerOrderRefundReadRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Lấy tổng số tiền đã hoàn của các đơn hàng.
     * Chỉ lấy refund thành công và thuộc thanh toán escrow.
     */
    public Map<Long, BigDecimal> refundedAmounts(
            List<Long> orderIds) {

        if (orderIds.isEmpty()) {
            return Map.of();
        }

        var rows = jdbc.queryForList("""
                SELECT
                    e.order_id,
                    COALESCE(SUM(r.amount), 0) AS refunded_amount
                FROM order_escrows e
                JOIN refunds r
                    ON r.payment_transaction_id =
                       e.payment_transaction_id
                   AND r.status = 'REFUNDED'
                WHERE e.order_id IN (:orderIds)
                GROUP BY e.order_id
                """,
                new MapSqlParameterSource(
                        "orderIds", orderIds)
        );

        Map<Long, BigDecimal> totals = new HashMap<>();

        for (Map<String, Object> row : rows) {
            Long orderId =
                    ((Number) row.get("order_id")).longValue();

            BigDecimal amount =
                    (BigDecimal) row.get("refunded_amount");

            totals.put(orderId, amount);
        }

        return totals;
    }
}