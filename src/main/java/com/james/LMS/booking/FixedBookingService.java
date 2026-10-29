package com.james.LMS.booking;

import com.james.LMS.entity.CookOrder;
import com.james.LMS.entity.Product;
import com.james.LMS.entity.User;
import com.james.LMS.enums.OrderServiceType;
import com.james.LMS.enums.OrderStatus;
import com.james.LMS.pricing.PricingService.FixedMenuQuote;
import com.james.LMS.repository.OrderRepository;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.request.PaymentCheckoutRequest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FixedBookingService {

    private final OrderRepository orders;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    /**
     * Caller phải chạy trong transaction.
     * Giá được lấy từ FixedMenuQuote do server tính.
     */
    public CookOrder createFixedDraft(
            Long customerId,
            String customerEmail,
            PaymentCheckoutRequest request,
            FixedMenuQuote quote,
            String orderCode
    ) {
        Product product = quote.product();

        User chef = users.findById(product.getSellerId())
                .filter(User::isActive)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Đầu bếp không còn hoạt động."
                        )
                );

        CookOrder order = CookOrder.builder()
                .orderCode(orderCode)
                .customerId(customerId)
                .customerName(request.customerName().trim())
                .customerEmail(customerEmail)
                .customerPhone(request.customerPhone().trim())
                .chefId(chef.getId())
                .chefName(chef.getUsername())
                .productName(product.getName())
                .serviceType(OrderServiceType.MENU)
                .guestCount(request.guestCount())
                .scheduledAt(request.scheduledAt())
                .serviceAddress(request.serviceAddress().trim())
                .customerNote(request.customerNote())
                .totalAmount(quote.total())
                .status(OrderStatus.PENDING)
                .build();

        order = orders.saveAndFlush(order);

        // Ghi snapshot sản phẩm và giá tại thời điểm đặt.
        // line_total là giá trước voucher.
        jdbc.update("""
                INSERT INTO order_items (
                    order_id,
                    product_id,
                    product_name_snapshot,
                    unit_price_snapshot,
                    guest_count,
                    quantity,
                    line_total,
                    note
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                order.getId(),
                product.getId(),
                product.getName(),
                product.getUnitPrice(),
                quote.guestCount(),
                1,
                quote.subtotal(),
                request.customerNote()
        );

        return order;
    }
}