package com.james.LMS.controller;

import static org.mockito.Mockito.*;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.service.CustomerOrderService;
import org.junit.jupiter.api.Test;

class CustomerOrderControllerTest {

    private final CustomerOrderService service =
            mock(CustomerOrderService.class);

    private final CustomerOrderController controller =
            new CustomerOrderController(service);

    private final SecurityUserDetails actor =
            SecurityUserDetails.builder()
                    .id(42L)
                    .build();

    @Test
    void listUsesAuthenticatedCustomerId() {

        controller.list(actor, "COMPLETED", 0, 10);

        verify(service).list(
                42L,
                "COMPLETED",
                0,
                10
        );
    }

    @Test
    void detailUsesAuthenticatedCustomerId() {

        controller.detail(actor, 11L);

        verify(service).get(42L, 11L);
    }
}