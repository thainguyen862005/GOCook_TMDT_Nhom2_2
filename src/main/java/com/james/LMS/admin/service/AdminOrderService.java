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
import com.james.LMS.order.OrderTransitionPolicy;
import com.james.LMS.order.OrderTransitionPolicy.Actor;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

  private final OrderRepository orderRepository;
  private final OrderTransitionPolicy transitionPolicy;
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

  @Transactional(readOnly = true)
  public CookOrder get(Long id) {
    return orderRepository
        .findById(id)
        .orElseThrow(() -> new AdminResourceNotFoundException("Không tìm thấy đơn hàng."));
  }

  @Transactional(readOnly = true)
public List<OrderStatus> allowedStatuses(Long id) {
    CookOrder order = get(id);

    List<OrderStatus> allowed = new java.util.ArrayList<>();
    allowed.add(order.getStatus());

    allowed.addAll(transitionPolicy.allowedTargets(
            order.getStatus(),
            Actor.ADMIN
    ));

    return allowed;
}

  @Transactional
public void update(
        Long id,
        OrderStatus status,
        String internalNote,
        Long actorId
) {
    if (status == null) {
        throw new IllegalArgumentException(
                "Thiếu trạng thái đơn hàng.");
    }

    if (internalNote != null && internalNote.length() > 2000) {
        throw new IllegalArgumentException(
                "Ghi chú nội bộ tối đa 2000 ký tự.");
    }

    // Khóa đơn trước khi đọc, tránh hai yêu cầu
    // cùng cập nhật từ một trạng thái cũ.
    CookOrder order = orderRepository.findByIdForUpdate(id)
            .orElseThrow(() ->
                    new AdminResourceNotFoundException(
                            "Không tìm thấy đơn hàng."));

    OrderStatus previous = order.getStatus();

    if (status != previous) {
        transitionPolicy.validate(
                previous,
                status,
                Actor.ADMIN,
                internalNote
        );
    }

    String note = cleanOptional(internalNote);

    order.setStatus(status);
    order.setInternalNote(note);

    orderRepository.saveAndFlush(order);

    if (status != previous) {
        jdbc.update("""
                INSERT INTO order_status_history(
                    order_id,
                    actor_user_id,
                    previous_status,
                    new_status,
                    note
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                actorId,
                previous.name(),
                status.name(),
                note == null
                    ? "Admin cập nhật trạng thái"
                    : note
        );
    }
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
