package com.james.LMS.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CustomerOrderRefundReadRepositoryTest {

    private final NamedParameterJdbcTemplate jdbc =
            mock(NamedParameterJdbcTemplate.class);

    private final CustomerOrderRefundReadRepository repository =
            new CustomerOrderRefundReadRepository(jdbc);

    @Test
    void emptyOrdersReturnEmptyMap() {

        var result = repository.refundedAmounts(List.of());

        assertThat(result).isEmpty();

        verifyNoInteractions(jdbc);
    }

    @Test
    void refundedAmountIsCalculated() {

        when(jdbc.queryForList(
                anyString(),
                any(MapSqlParameterSource.class)
        )).thenReturn(
                List.of(Map.of(
                        "order_id", 42L,
                        "refunded_amount",
                        new BigDecimal("150000")
                ))
        );

        var result = repository.refundedAmounts(
                List.of(42L)
        );

        assertThat(result.get(42L))
                .isEqualByComparingTo("150000");
    }
}