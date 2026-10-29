package com.james.LMS.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.james.LMS.entity.CookOrder;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.repository.OrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {
  @Mock OrderRepository orders;
  @Mock org.springframework.jdbc.core.JdbcTemplate jdbc;
  @InjectMocks AdminOrderService service;

  @Test
  void cannotJumpFromPendingToCompleted() {
    when(orders.findById(10L)).thenReturn(Optional.of(order(OrderStatus.PENDING)));
    assertThrows(IllegalArgumentException.class,
        () -> service.update(10L, OrderStatus.COMPLETED, ""));
    verify(orders, never()).save(any());
  }

  @Test
  void cannotCompleteWithoutPaymentFlow() {
    when(orders.findById(10L)).thenReturn(Optional.of(order(OrderStatus.AWAITING_ACCEPTANCE)));
    assertThrows(IllegalArgumentException.class,
        () -> service.update(10L, OrderStatus.COMPLETED, ""));
    verify(orders, never()).save(any());
  }

  @Test
  void internalNoteIsEditable() {
    CookOrder order = order(OrderStatus.CONFIRMED);
    when(orders.findById(10L)).thenReturn(Optional.of(order));
    service.update(10L, OrderStatus.CONFIRMED, "Ít cay");
    assertEquals("Ít cay", order.getInternalNote());
    verify(orders).save(order);
  }

  @Test
  void invalidFilterIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> service.search("", "UNKNOWN", null, null, 0, 10));
  }

  private CookOrder order(OrderStatus status) {
    return CookOrder.builder().id(10L).orderCode("GC-A01-DEMO-001").status(status).build();
  }
}
