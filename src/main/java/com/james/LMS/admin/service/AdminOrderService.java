package com.james.LMS.admin.service;

import com.james.LMS.admin.exception.AdminResourceNotFoundException;
import com.james.LMS.entity.CookOrder;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.repository.OrderRepository;
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

  @Transactional(readOnly = true)
  public CookOrder get(Long id) {
    return orderRepository
        .findById(id)
        .orElseThrow(() -> new AdminResourceNotFoundException("Không tìm thấy đơn hàng."));
  }

  @Transactional(readOnly = true)
  public List<OrderStatus> allowedStatuses(Long id) {
    CookOrder order = get(id);
    return order.getStatus().allowedTargets().stream()
        .filter(target -> target == order.getStatus()
            || (target != OrderStatus.COMPLETED
                && target != OrderStatus.CANCELLED && target != OrderStatus.REJECTED))
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
    if (status != order.getStatus() && (status == OrderStatus.COMPLETED
        || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED)) {
      throw new IllegalArgumentException("Trạng thái kết thúc cần qua Booking/Payment để đồng bộ hoàn tiền và escrow.");
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
