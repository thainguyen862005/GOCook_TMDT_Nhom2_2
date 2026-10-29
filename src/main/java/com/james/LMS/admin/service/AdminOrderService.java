package com.james.LMS.admin.service;

import com.james.LMS.admin.exception.AdminResourceNotFoundException;
import com.james.LMS.entity.CookOrder;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.repository.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.HashMap;

import com.james.LMS.admin.util.AdminPaging;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

  private final OrderRepository orderRepository;
  private final JdbcTemplate jdbc;


  @Transactional(readOnly = true)
  public Page<CookOrder> search(
      String query,
      String status,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("Ngày bắt đầu không thể sau ngày kết thúc.");
    }

    String normalizedQuery = normalize(query).toLowerCase(Locale.ROOT);
    OrderStatus parsedStatus = parseStatus(status);
    LocalDateTime fromDate = from == null ? null : from.atStartOfDay();
    LocalDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay();

    Specification<CookOrder> spec = (root, criteriaQuery, cb) -> cb.conjunction();

    if (!normalizedQuery.isBlank()) {
      String pattern = "%" + normalizedQuery + "%";
      spec =
          spec.and(
              (root, criteriaQuery, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("orderCode")), pattern),
                      cb.like(cb.lower(root.get("customerName")), pattern),
                      cb.like(cb.lower(root.get("customerEmail")), pattern),
                      cb.like(cb.lower(root.get("productName")), pattern),
                      cb.like(cb.lower(root.get("chefName")), pattern)));
    }

    if (parsedStatus != null) {
      spec =
          spec.and(
              (root, criteriaQuery, cb) -> cb.equal(root.get("status"), parsedStatus));
    }

    if (fromDate != null) {
      spec =
          spec.and(
              (root, criteriaQuery, cb) ->
                  cb.greaterThanOrEqualTo(root.get("scheduledAt"), fromDate));
    }

    if (toExclusive != null) {
      spec =
          spec.and(
              (root, criteriaQuery, cb) ->
                  cb.lessThan(root.get("scheduledAt"), toExclusive));
    }

    Pageable pageable =
        AdminPaging.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "scheduledAt"));

    return orderRepository.findAll(spec, pageable);
  }

  /** Authoritative label: never infer payment from order.status='PENDING'. */
  @Transactional(readOnly = true)
  public String paymentState(Long orderId) {
    var records = jdbc.query("""
        SELECT CASE WHEN e.status IS NOT NULL THEN e.status
          WHEN EXISTS(SELECT 1 FROM payment_transactions p WHERE p.order_id=o.id AND p.status='SUCCESS')
            THEN 'VERIFYING_ESCROW'
          WHEN EXISTS(SELECT 1 FROM payment_transactions p WHERE p.order_id=o.id AND p.status='PENDING')
            THEN 'VNPAY_PENDING'
          ELSE 'UNPAID' END AS payment_state
        FROM orders o LEFT JOIN order_escrows e ON e.order_id=o.id WHERE o.id=?
        """, (rs,n) -> rs.getString(1), orderId);
    return records.isEmpty() ? "UNPAID" : records.get(0);
  }

  @Transactional(readOnly = true)
  public Map<Long,String> paymentStates(List<CookOrder> orders) {
    Map<Long,String> result = new HashMap<>();
    for (CookOrder order: orders) result.put(order.getId(), paymentState(order.getId()));
    return result;
  }

  @Transactional(readOnly = true)
  public CookOrder get(Long id) {
    return orderRepository
        .findById(id)
        .orElseThrow(() -> new AdminResourceNotFoundException("Không tìm thấy đơn hàng."));
  }

  @Transactional(readOnly = true)
  public List<OrderStatus> allowedStatuses(Long id) {
    CookOrder order = get(id);
    // Booking actions belong to Chef/Customer; Admin can edit the internal note only.
    return order.getStatus().allowedTargets().stream()
        .filter(target -> target == order.getStatus())
        .toList();
  }

  @Transactional
  public void update(Long id, OrderStatus status, String internalNote) {
    CookOrder order = get(id);
    if (status == null || !order.getStatus().canTransitionTo(status)) {
      throw new IllegalArgumentException(
          "Không thể chuyển đơn từ “"
              + order.getStatus().getLabel()
              + "” sang trạng thái đã chọn.");
    }
    if (status != order.getStatus()) {
      throw new IllegalArgumentException("Admin chỉ được ghi chú. Chef nhận đơn sau khi có escrow HELD; các trạng thái còn lại phải đi qua workflow chuyên trách.");
    }
    if (internalNote != null && internalNote.length() > 2000) {
      throw new IllegalArgumentException("Ghi chú nội bộ tối đa 2000 ký tự.");
    }
    order.setStatus(status);
    order.setInternalNote(cleanOptional(internalNote));
    orderRepository.save(order);
  }

  @Transactional(readOnly = true)
  public List<CookOrder> findForStatistics(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("Ngày bắt đầu không thể sau ngày kết thúc.");
    }
    LocalDateTime fromDate = from == null ? null : from.atStartOfDay();
    LocalDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
    Specification<CookOrder> spec = (root, criteriaQuery, cb) -> cb.conjunction();
    if (fromDate != null) {
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("scheduledAt"), fromDate));
    }
    if (toExclusive != null) {
      spec = spec.and((root, query, cb) -> cb.lessThan(root.get("scheduledAt"), toExclusive));
    }
    return orderRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "scheduledAt"));
  }

  public OrderStatus[] statuses() {
    return OrderStatus.values();
  }

  private OrderStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("Bộ lọc trạng thái đơn không hợp lệ.");
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private String cleanOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
