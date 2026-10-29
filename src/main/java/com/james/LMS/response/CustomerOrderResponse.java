package com.james.LMS.response;

import com.james.LMS.entity.CookOrder;
import com.james.LMS.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Public customer-facing fields only. Do not expose internalNote or JPA entities. */
public record CustomerOrderResponse(
    Long id,
    String orderCode,
    String productName,
    String chefName,
    Integer guestCount,
    LocalDateTime scheduledAt,
    BigDecimal totalAmount,
    OrderStatus status,
    Long createdAt,
    String serviceAddress,
    String customerNote,
    String escrowStatus) {

  public static CustomerOrderResponse from(CookOrder order, String escrowStatus) {
    return new CustomerOrderResponse(
        order.getId(), 
        order.getOrderCode(), 
        order.getProductName(),
        order.getChefName(), 
        order.getGuestCount(), 
        order.getScheduledAt(),
        order.getTotalAmount(),
        order.getStatus(), 
        order.getCreatedAt(),
        order.getServiceAddress(),
        order.getCustomerNote(),
        escrowStatus);
  }
}
