package com.james.LMS.admin.operations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AdminOperationsServiceTest {
    @Mock NamedParameterJdbcTemplate jdbc;
    @Mock AdminAuditService audit;
    @Mock com.james.LMS.notification.NotificationService notifications;
    @InjectMocks AdminOperationsService service;

    @Test
    void rejectsUnknownModuleBeforeQueryingDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.page("other", "", "", 0, 10));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsInvalidPageAndPageSize() {
        assertThrows(IllegalArgumentException.class,
                () -> service.page("customers", "", "", -1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> service.page("customers", "", "", 0, 101));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNonBooleanCustomerFilter() {
        assertThrows(IllegalArgumentException.class,
                () -> service.page("customers", "", "ACTIVE", 0, 10));
        verifyNoInteractions(jdbc);
    }

    @Test
    void refusesUnsafeVoucherDiscount() {
        assertThrows(IllegalArgumentException.class, () -> service.create("vouchers", Map.of(
                "code", "BIGDISCOUNT", "discountType", "PERCENT", "discountValue", "150",
                "startsAt", "2026-10-01T00:00:00+07:00", "endsAt", "2026-12-31T00:00:00+07:00"), 1L));
        verifyNoInteractions(jdbc);
    }

    @Test
    void refusesCreationOfPaymentOrAuditRecord() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("payments", Map.of(), 1L));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("audit", Map.of(), 1L));
        verifyNoInteractions(jdbc);
    }
}
