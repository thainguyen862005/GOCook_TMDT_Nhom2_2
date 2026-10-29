package com.james.LMS.admin.controller;

import com.james.LMS.admin.dto.request.UpdateOrderRequest;
import com.james.LMS.admin.dto.response.AdminOrdersResponse;
import com.james.LMS.entity.CookOrder;
import com.james.LMS.admin.service.AdminOrderService;
import java.time.LocalDate;
import java.util.Map;
import com.james.LMS.config.SecurityUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.james.LMS.admin.operations.AdminAuditService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class AdminOrderController {

  private final AdminOrderService orderService;
  private final AdminAuditService audit;

  @GetMapping
  public AdminOrdersResponse search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    Page<CookOrder> result = orderService.search(q, status, from, to, page, size);
    return AdminOrdersResponse.from(result, orderService.statuses());
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable Long id) {
    return Map.of("order", orderService.get(id), "allowedStatuses", orderService.allowedStatuses(id));
  }

  @PutMapping("/{id}")
  @Transactional
  public Map<String, Object> update(@PathVariable Long id, @RequestBody UpdateOrderRequest request,
      @AuthenticationPrincipal SecurityUserDetails actor) {
    orderService.update(
        id,
        request.status(),
        request.internalNote(),
        actor.getId());
    audit.record(actor.getId(), "ORDER_ADMIN_EDIT", "ORDER", id, "Cập nhật ghi chú/trạng thái theo rule Admin");
    return Map.of(
        "message", "Đã cập nhật đơn hàng.",
        "order", orderService.get(id),
        "allowedStatuses", orderService.allowedStatuses(id));
  }
}
