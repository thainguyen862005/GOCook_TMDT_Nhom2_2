package com.james.LMS.order;

import com.james.LMS.enums.OrderStatus;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderTransitionPolicy {

    public enum Actor {
        CUSTOMER,
        CHEF,
        ADMIN,
        PAYMENT
    }

    public List<OrderStatus> allowedTargets(
            OrderStatus current,
            Actor actor
    ) {
        if (current == null || actor == null) {
            return List.of();
        }

        return current.allowedTargets().stream()
                .filter(target ->
                        target != current
                        && isAllowedActor(current, target, actor))
                .toList();
    }

    public void validate(
            OrderStatus current,
            OrderStatus target,
            Actor actor,
            String reason
    ) {
        if (current == null || target == null || actor == null) {
            throw new IllegalArgumentException(
                    "Thiếu thông tin chuyển trạng thái.");
        }

        if (current == target) {
            throw new IllegalArgumentException(
                    "Trạng thái đơn hàng không thay đổi.");
        }

        if (!current.canTransitionTo(target)) {
            throw new IllegalArgumentException(
                    "Không thể chuyển đơn từ "
                    + current.name() + " sang " + target.name());
        }

        if (!isAllowedActor(current, target, actor)) {
            throw new IllegalArgumentException(
                    "Vai trò " + actor.name()
                    + " không được thực hiện chuyển trạng thái này.");
        }

        String normalizedReason =
                reason == null ? "" : reason.trim();

        if (normalizedReason.length() > 2000) {
            throw new IllegalArgumentException(
                    "Lý do không được vượt quá 2000 ký tự.");
        }

        if (target == OrderStatus.REJECTED
                && normalizedReason.length() < 10) {
            throw new IllegalArgumentException(
                    "Từ chối đơn phải có lý do ít nhất 10 ký tự.");
        }
    }

    private boolean isAllowedActor(
            OrderStatus current,
            OrderStatus target,
            Actor actor
    ) {
        return switch (actor) {

            case CHEF -> switch (target) {
                case CONFIRMED, PREPARING,
                     COOKING, AWAITING_ACCEPTANCE -> true;
                case REJECTED ->
                        current == OrderStatus.PENDING;
                default -> false;
            };

            case ADMIN -> target == OrderStatus.CONFIRMED
                    || target == OrderStatus.PREPARING
                    || target == OrderStatus.COOKING;

            case CUSTOMER -> false;

            case PAYMENT -> target == OrderStatus.COMPLETED
                    || target == OrderStatus.CANCELLED
                    || target == OrderStatus.REJECTED;
        };
    }
}