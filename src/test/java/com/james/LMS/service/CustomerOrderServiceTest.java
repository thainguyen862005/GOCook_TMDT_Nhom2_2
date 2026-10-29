package com.james.LMS.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.james.LMS.entity.CookOrder;
import com.james.LMS.entity.PaymentTransaction;
import com.james.LMS.enums.OrderServiceType;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.enums.PaymentStatus;
import com.james.LMS.repository.OrderRepository;
import com.james.LMS.repository.PaymentTransactionRepository;
import com.james.LMS.repository.CustomerOrderRefundReadRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceTest {

    private static final Long CUSTOMER_ID = 42L;

    @Mock
    OrderRepository orderRepository;

    @Mock
    PaymentTransactionRepository paymentRepository;

    @Mock
    CustomerOrderRefundReadRepository refundRepository;

    @InjectMocks
    CustomerOrderService service;

    @Test
    void customerOnlySeesOwnOrders() {

        var page = new PageImpl<CookOrder>(List.of());

        when(orderRepository.findByCustomerIdAndIsActiveTrue(
                eq(CUSTOMER_ID),
                any(Pageable.class)
        )).thenReturn(page);

        var result = service.list(
                CUSTOMER_ID,
                "",
                0,
                10
        );

        assertThat(result.content()).isEmpty();

        verify(orderRepository)
                .findByCustomerIdAndIsActiveTrue(
                        eq(CUSTOMER_ID),
                        any(Pageable.class)
                );
    }

    @Test
    void foreignOrderReturns404() {

        when(orderRepository
                .findByIdAndCustomerIdAndIsActiveTrue(
                        999L,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.get(CUSTOMER_ID, 999L)
        ).isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND)
        );

        verifyNoInteractions(
                paymentRepository,
                refundRepository
        );
    }

    @Test
    void invalidStatusReturns400() {

        assertThatThrownBy(() ->
                service.list(
                        CUSTOMER_ID,
                        "INVALID",
                        0,
                        10
                )
        ).isInstanceOfSatisfying(
                ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    @Test
    void invalidPageReturns400() {

        assertThatThrownBy(() ->
                service.list(
                        CUSTOMER_ID,
                        "",
                        -1,
                        10
                )
        ).isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() ->
                service.list(
                        CUSTOMER_ID,
                        "",
                        0,
                        51
                )
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void customBookingKeepsSnapshot() {

        CookOrder order = CookOrder.builder()
                .id(100L)
                .customerId(CUSTOMER_ID)
                .orderCode("GC-100")
                .serviceType(OrderServiceType.BUDGET)
                .productName(null)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("500000"))
                .build();

        when(orderRepository
                .findByIdAndCustomerIdAndIsActiveTrue(
                        100L,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(order));

        var result = service.get(
                CUSTOMER_ID,
                100L
        );

        assertThat(result.serviceType())
                .isEqualTo(OrderServiceType.BUDGET);

        assertThat(result.productName()).isNull();

        assertThat(result.totalAmount())
                .isEqualByComparingTo("500000");
    }

    @Test
    void successfulPaymentIsNotHiddenByPendingRetry() {

        CookOrder order = CookOrder.builder()
                .id(101L)
                .customerId(CUSTOMER_ID)
                .orderCode("GC-101")
                .serviceType(OrderServiceType.MENU)
                .build();

        PaymentTransaction pending =
                PaymentTransaction.builder()
                        .id(20L)
                        .orderId(101L)
                        .status(PaymentStatus.PENDING)
                        .build();

        PaymentTransaction success =
                PaymentTransaction.builder()
                        .id(19L)
                        .orderId(101L)
                        .status(PaymentStatus.SUCCESS)
                        .build();

        when(orderRepository
                .findByIdAndCustomerIdAndIsActiveTrue(
                        101L,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(order));

        when(paymentRepository
                .findByOrderIdInAndIsActiveTrueOrderByCreatedAtDescIdDesc(
                        List.of(101L)
                ))
                .thenReturn(List.of(pending, success));

        var result = service.get(CUSTOMER_ID, 101L);

        assertThat(result.paymentStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void partialRefundAmountIsReturned() {

        CookOrder order = CookOrder.builder()
                .id(102L)
                .customerId(CUSTOMER_ID)
                .orderCode("GC-102")
                .serviceType(OrderServiceType.MENU)
                .build();

        when(orderRepository
                .findByIdAndCustomerIdAndIsActiveTrue(
                        102L,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(order));

        when(refundRepository.refundedAmounts(
                List.of(102L)
        )).thenReturn(
                Map.of(102L, new BigDecimal("150000"))
        );

        var result = service.get(CUSTOMER_ID, 102L);

        assertThat(result.refundedAmount())
                .isEqualByComparingTo("150000");
    }
}