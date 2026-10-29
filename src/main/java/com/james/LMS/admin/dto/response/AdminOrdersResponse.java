package com.james.LMS.admin.dto.response;

import com.james.LMS.entity.CookOrder;
import com.james.LMS.enums.OrderStatus;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public record AdminOrdersResponse(
    List<CookOrder> content,
    int number,
    int size,
    int numberOfElements,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last,
    OrderStatus[] statuses,
    Map<Long, String> paymentStates) {

  public static AdminOrdersResponse from(Page<CookOrder> page, OrderStatus[] statuses, Map<Long, String> paymentStates) {
    return new AdminOrdersResponse(
        page.getContent(), page.getNumber(), page.getSize(), page.getNumberOfElements(),
        page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast(), statuses, paymentStates);
  }
}
