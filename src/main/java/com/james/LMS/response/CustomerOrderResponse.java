package com.james.LMS.response;

import com.james.LMS.entity.CookOrder;
import com.james.LMS.entity.PaymentTransaction;

import com.james.LMS.enums.OrderStatus;
import com.james.LMS.enums.OrderServiceType;
import com.james.LMS.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record CustomerOrderResponse(

    Long id,
    String orderCode,

    OrderServiceType serviceType,

    String productName,
    String chefName,
    Integer guestCount,

    LocalDateTime scheduledAt,
    BigDecimal totalAmount,

    OrderStatus status,
    Long createdAt,

    String serviceAddress,
    String customerNote,

    PaymentStatus paymentStatus,
    OffsetDateTime paidAt,

    BigDecimal refundedAmount

) {

    public static CustomerOrderResponse from(
            CookOrder order,
            PaymentTransaction payment,
            BigDecimal refundedAmount
    ) {

        return new CustomerOrderResponse(

            order.getId(),
            order.getOrderCode(),

            order.getServiceType(),

            order.getProductName(),
            order.getChefName(),
            order.getGuestCount(),

            order.getScheduledAt(),
            order.getTotalAmount(),

            order.getStatus(),
            order.getCreatedAt(),

            order.getServiceAddress(),
            order.getCustomerNote(),

            payment == null
                ? null
                : payment.getStatus(),

            payment == null
                ? null
                : payment.getPaidAt(),

            refundedAmount == null
                ? BigDecimal.ZERO
                : refundedAmount

        );
    }
}