package com.james.LMS.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.james.LMS.enums.OrderServiceType;
import org.junit.jupiter.api.Test;

class CookOrderServiceTypeTest {
  @Test
  void existingMenuCheckoutDefaultsToMenu() {
    assertEquals(OrderServiceType.MENU, CookOrder.builder().build().getServiceType());
  }

  @Test
  void futureBudgetCheckoutCanSetBudgetExplicitly() {
    assertEquals(OrderServiceType.BUDGET,
        CookOrder.builder().serviceType(OrderServiceType.BUDGET).build().getServiceType());
  }
}
