
package com.james.LMS.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.james.LMS.entity.CookOrder;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.order.OrderTransitionPolicy;
import com.james.LMS.repository.OrderRepository;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

  @Mock
  OrderRepository orders;

  @Mock
  JdbcTemplate jdbc;

  @Spy
  OrderTransitionPolicy transitionPolicy = new OrderTransitionPolicy();

  @InjectMocks
  AdminOrderService service;

  private static final Long ADMIN_ID = 1L;

  @Test
  void cannotJumpFromPendingToCompleted() {

    when(orders.findByIdForUpdate(10L))
        .thenReturn(Optional.of(
            order(OrderStatus.PENDING)));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.update(
            10L,
            OrderStatus.COMPLETED,
            "",
            ADMIN_ID));

    verify(orders, never()).saveAndFlush(any());
    verifyNoInteractions(jdbc);
  }

  @Test
  void cannotCompleteWithoutPaymentFlow() {

    when(orders.findByIdForUpdate(10L))
        .thenReturn(Optional.of(
            order(OrderStatus.AWAITING_ACCEPTANCE)));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.update(
            10L,
            OrderStatus.COMPLETED,
            "",
            ADMIN_ID));

    verify(orders, never()).saveAndFlush(any());
    verifyNoInteractions(jdbc);
  }

  @Test
  void internalNoteIsEditable() {

    CookOrder order = order(OrderStatus.CONFIRMED);

    when(orders.findByIdForUpdate(10L))
        .thenReturn(Optional.of(order));

    service.update(
        10L,
        OrderStatus.CONFIRMED,
        "Ít cay",
        ADMIN_ID);

    assertEquals("Ít cay", order.getInternalNote());

    verify(orders).saveAndFlush(order);

    // Chỉ sửa ghi chú, không đổi trạng thái.
    // Vì vậy không tạo lịch sử trạng thái mới.
    verifyNoInteractions(jdbc);
  }

  @Test
  void invalidFilterIsRejected() {

    assertThrows(
        IllegalArgumentException.class,
        () -> service.search(
            "",
            "UNKNOWN",
            null,
            null,
            0,
            10));
  }

  private CookOrder order(OrderStatus status) {

    return CookOrder.builder()
        .id(10L)
        .orderCode("GC-A01-DEMO-001")
        .status(status)
        .build();
  }
}
