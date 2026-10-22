package com.james.LMS.payment.finance;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.payment.PaymentTime;
import com.james.LMS.service.PaymentGateway;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * What a chef earns, from order_escrows only. Money refunded or waiting for a refund is not the
 * chef's and is left out. The chef id always comes from the caller's login, never from a request.
 */
@Service
@RequiredArgsConstructor
public class SellerFinanceService {

  static final int MAX_PAGE_SIZE = 100;
  static final int MAX_MONTHS = 24;

  private final NamedParameterJdbcTemplate jdbc;

  public record Summary(
      BigDecimal availableAmount,
      BigDecimal pendingAmount,
      BigDecimal platformFeeTotal,
      long releasedOrderCount,
      long menuOrderCount,
      long budgetOrderCount) {}

  public record MonthlyIncome(String month, BigDecimal earnedAmount, long orderCount) {}

  public record Transaction(
      String orderCode,
      BigDecimal grossAmount,
      BigDecimal platformFee,
      BigDecimal platformFeePercent,
      BigDecimal chefAmount,
      String status,
      String heldAt,
      String releasedAt) {}

  public record TransactionPage(
      List<Transaction> content, int page, int size, long totalElements, int totalPages) {}

  /** Released money is dated by released_at, money still held by held_at. */
  public Summary summary(Long chefId, FinanceRange range) {
    MapSqlParameterSource params = params(chefId, range);
    String released = "e.status = 'RELEASED'" + bounds("e.released_at", range);
    String held = "e.status = 'HELD'" + bounds("e.held_at", range);
    String paid = "e.status IN ('HELD', 'RELEASED')" + bounds("e.held_at", range);
    return jdbc.queryForObject(
        """
        SELECT COALESCE(SUM(e.chef_amount) FILTER (WHERE %s), 0) AS available,
               COALESCE(SUM(e.chef_amount) FILTER (WHERE %s), 0) AS pending,
               COALESCE(SUM(e.platform_fee) FILTER (WHERE %s), 0) AS fee,
               COUNT(*) FILTER (WHERE %s) AS released_count,
               COUNT(*) FILTER (WHERE %s AND o.service_type = 'MENU') AS menu_count,
               COUNT(*) FILTER (WHERE %s AND o.service_type = 'BUDGET') AS budget_count
        FROM order_escrows e JOIN orders o ON o.id = e.order_id
        WHERE e.chef_id = :chef AND e.status IN ('HELD', 'RELEASED')
        """
            .formatted(released, held, released, released, paid, paid),
        params,
        (row, n) ->
            new Summary(
                row.getBigDecimal("available"),
                row.getBigDecimal("pending"),
                row.getBigDecimal("fee"),
                row.getLong("released_count"),
                row.getLong("menu_count"),
                row.getLong("budget_count")));
  }

  /** The last {@code months} months up to the current one, oldest first, zero months included. */
  public List<MonthlyIncome> monthly(Long chefId, int months) {
    if (months < 1 || months > MAX_MONTHS) {
      throw invalid("months phải từ 1 đến " + MAX_MONTHS + ".");
    }
    List<YearMonth> window = lastMonths(YearMonth.now(PaymentGateway.GATEWAY_ZONE), months);
    MapSqlParameterSource params =
        new MapSqlParameterSource("chef", chefId)
            .addValue("first", startOf(window.get(0)))
            .addValue("end", startOf(window.get(window.size() - 1).plusMonths(1)));

    Map<String, MonthlyIncome> found = new HashMap<>();
    jdbc.query(
        """
        SELECT to_char(date_trunc('month', e.released_at AT TIME ZONE 'Asia/Ho_Chi_Minh'), 'YYYY-MM') AS month,
               COALESCE(SUM(e.chef_amount), 0) AS earned, COUNT(*) AS orders
        FROM order_escrows e
        WHERE e.chef_id = :chef AND e.status = 'RELEASED'
          AND e.released_at >= :first AND e.released_at < :end
        GROUP BY 1
        """,
        params,
        (row, n) ->
            found.put(
                row.getString("month"),
                new MonthlyIncome(
                    row.getString("month"), row.getBigDecimal("earned"), row.getLong("orders"))));

    List<MonthlyIncome> result = new ArrayList<>();
    for (YearMonth month : window) {
      result.add(found.getOrDefault(month.toString(), new MonthlyIncome(month.toString(), BigDecimal.ZERO, 0)));
    }
    return result;
  }

  public TransactionPage transactions(
      Long chefId, int page, int size, String status, FinanceRange range) {
    if (page < 0 || size < 1) {
      throw invalid("page phải từ 0 và size phải từ 1.");
    }
    if (status != null && !status.equals("HELD") && !status.equals("RELEASED")) {
      throw invalid("status chỉ có thể là HELD hoặc RELEASED.");
    }
    int pageSize = Math.min(size, MAX_PAGE_SIZE);
    MapSqlParameterSource params =
        params(chefId, range).addValue("limit", pageSize).addValue("offset", (long) page * pageSize);
    String statusFilter = "";
    if (status != null) {
      statusFilter = " AND e.status = :status";
      params.addValue("status", status);
    }

    long[] total = {0};
    List<Transaction> rows =
        jdbc.query(
            """
            SELECT o.order_code, e.gross_amount, e.platform_fee, e.chef_amount, e.status, e.held_at,
                   e.released_at, COUNT(*) OVER () AS total
            FROM order_escrows e JOIN orders o ON o.id = e.order_id
            WHERE e.chef_id = :chef AND e.status IN ('HELD', 'RELEASED')%s%s
            ORDER BY COALESCE(e.released_at, e.held_at) DESC, e.id DESC
            LIMIT :limit OFFSET :offset
            """
                .formatted(statusFilter, bounds("COALESCE(e.released_at, e.held_at)", range)),
            params,
            (row, n) -> {
              total[0] = row.getLong("total");
              BigDecimal gross = row.getBigDecimal("gross_amount");
              BigDecimal fee = row.getBigDecimal("platform_fee");
              return new Transaction(
                  row.getString("order_code"),
                  gross,
                  fee,
                  percent(fee, gross),
                  row.getBigDecimal("chef_amount"),
                  row.getString("status"),
                  PaymentTime.iso(row.getObject("held_at", OffsetDateTime.class)),
                  PaymentTime.iso(row.getObject("released_at", OffsetDateTime.class)));
            });
    int pages = (int) ((total[0] + pageSize - 1) / pageSize);
    return new TransactionPage(rows, page, pageSize, total[0], pages);
  }

  /** The fee as a share of the order, read from the row itself (fee / gross), two decimals. */
  static BigDecimal percent(BigDecimal fee, BigDecimal gross) {
    return gross.signum() == 0
        ? BigDecimal.ZERO
        : fee.multiply(BigDecimal.valueOf(100)).divide(gross, 2, RoundingMode.HALF_UP);
  }

  static List<YearMonth> lastMonths(YearMonth current, int months) {
    List<YearMonth> window = new ArrayList<>();
    for (int back = months - 1; back >= 0; back--) {
      window.add(current.minusMonths(back));
    }
    return window;
  }

  static OffsetDateTime startOf(YearMonth month) {
    return PaymentTime.startOfDay(month.atDay(1));
  }

  private static MapSqlParameterSource params(Long chefId, FinanceRange range) {
    MapSqlParameterSource params = new MapSqlParameterSource("chef", chefId);
    if (range.fromInclusive() != null) {
      params.addValue("from", range.fromInclusive());
    }
    if (range.toExclusive() != null) {
      params.addValue("to", range.toExclusive());
    }
    return params;
  }

  private static String bounds(String column, FinanceRange range) {
    String sql = "";
    if (range.fromInclusive() != null) {
      sql += " AND " + column + " >= :from";
    }
    if (range.toExclusive() != null) {
      sql += " AND " + column + " < :to";
    }
    return sql;
  }

  private static PaymentException invalid(String message) {
    return new PaymentException(HttpStatus.BAD_REQUEST, ErrorCode.FINANCE_INVALID_PARAM, message);
  }
}
