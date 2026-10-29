package com.james.LMS.order;

import com.james.LMS.enums.OrderStatus;
import com.james.LMS.order.OrderTransitionPolicy.Actor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderTransitionPolicyTest {

    private OrderTransitionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new OrderTransitionPolicy();
    }

    @Test
    void chefCanConfirmPendingOrder() {
        assertThatCode(() ->
                policy.validate(
                        OrderStatus.PENDING,
                        OrderStatus.CONFIRMED,
                        Actor.CHEF,
                        null
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void chefCannotJumpFromPendingToCooking() {
        assertThatThrownBy(() ->
                policy.validate(
                        OrderStatus.PENDING,
                        OrderStatus.COOKING,
                        Actor.CHEF,
                        null
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotCancelCompletedOrder() {
        assertThatThrownBy(() ->
                policy.validate(
                        OrderStatus.COMPLETED,
                        OrderStatus.CANCELLED,
                        Actor.PAYMENT,
                        "Khách yêu cầu hủy"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chefCannotCompleteOrder() {
        assertThatThrownBy(() ->
                policy.validate(
                        OrderStatus.AWAITING_ACCEPTANCE,
                        OrderStatus.COMPLETED,
                        Actor.CHEF,
                        null
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminCannotCancelDirectly() {
        assertThatThrownBy(() ->
                policy.validate(
                        OrderStatus.CONFIRMED,
                        OrderStatus.CANCELLED,
                        Actor.ADMIN,
                        "Hủy đơn"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chefMustProvideReasonWhenRejecting() {
        assertThatThrownBy(() ->
                policy.validate(
                        OrderStatus.PENDING,
                        OrderStatus.REJECTED,
                        Actor.CHEF,
                        "Không"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chefCanRejectPendingWithReason() {
        assertThatCode(() ->
                policy.validate(
                        OrderStatus.PENDING,
                        OrderStatus.REJECTED,
                        Actor.CHEF,
                        "Không thể phục vụ lịch này"
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void paymentCanCompleteAwaitingAcceptance() {
        assertThatCode(() ->
                policy.validate(
                        OrderStatus.AWAITING_ACCEPTANCE,
                        OrderStatus.COMPLETED,
                        Actor.PAYMENT,
                        null
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void cannotChangeTerminalStatus() {
        for (OrderStatus current : new OrderStatus[]{
                OrderStatus.COMPLETED,
                OrderStatus.CANCELLED,
                OrderStatus.REJECTED
        }) {
            assertThat(policy.allowedTargets(
                    current,
                    Actor.ADMIN
            )).isEmpty();
        }
    }
}