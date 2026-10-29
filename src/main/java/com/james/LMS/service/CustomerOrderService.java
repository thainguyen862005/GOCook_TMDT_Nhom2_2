package com.james.LMS.service;

import com.james.LMS.entity.CookOrder;
import com.james.LMS.entity.PaymentTransaction;

import com.james.LMS.enums.OrderStatus;
import com.james.LMS.enums.PaymentStatus;

import com.james.LMS.repository.OrderRepository;
import com.james.LMS.repository.PaymentTransactionRepository;
import com.james.LMS.repository.CustomerOrderRefundReadRepository;

import com.james.LMS.response.CustomerOrderResponse;
import com.james.LMS.response.CustomerOrdersPageResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE_NUMBER = 100_000;

    private final OrderRepository orderRepository;

    private final PaymentTransactionRepository paymentRepository;

    private final CustomerOrderRefundReadRepository refundRepository;

    /**
     * Lấy lịch sử đơn hàng của khách đang đăng nhập.
     */
    @Transactional(readOnly = true)
    public CustomerOrdersPageResponse list(
            Long customerId,
            String status,
            int page,
            int size
    ) {

        // Kiểm tra phân trang
        if (page < 0 || page > MAX_PAGE_NUMBER
                || size < 1 || size > MAX_PAGE_SIZE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page hoặc size không hợp lệ"
            );
        }

        // Chuyển chuỗi trạng thái thành enum
        OrderStatus parsedStatus = parseStatus(status);

        // Sắp xếp đơn mới nhất trước
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
                    .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        // Chỉ truy vấn đơn của customer hiện tại
        Page<CookOrder> orders = parsedStatus == null
            ? orderRepository.findByCustomerIdAndIsActiveTrue(
                    customerId,
                    pageable
              )
            : orderRepository.findByCustomerIdAndStatusAndIsActiveTrue(
                    customerId,
                    parsedStatus,
                    pageable
              );

        // Danh sách ID đơn trên trang hiện tại
        List<Long> orderIds = orders.getContent()
                .stream()
                .map(CookOrder::getId)
                .toList();

        // Lấy thông tin thanh toán theo batch
        Map<Long, PaymentTransaction> paymentByOrder =
                paymentSummaries(orderIds);

        // Lấy tiền hoàn theo batch
        Map<Long, java.math.BigDecimal> refundByOrder =
                refundRepository.refundedAmounts(orderIds);

        // Chuyển entity sang response
        return CustomerOrdersPageResponse.from(
                orders.map(order ->
                        CustomerOrderResponse.from(
                                order,
                                paymentByOrder.get(order.getId()),
                                refundByOrder.get(order.getId())
                        )
                )
        );
    }

    /**
     * Lấy chi tiết đơn hàng.
     * Không cho khách xem đơn của người khác.
     */
    @Transactional(readOnly = true)
    public CustomerOrderResponse get(
            Long customerId,
            Long orderId
    ) {

        CookOrder order = orderRepository
                .findByIdAndCustomerIdAndIsActiveTrue(
                        orderId,
                        customerId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy đơn hàng"
                        )
                );

        List<Long> orderIds = List.of(order.getId());

        PaymentTransaction payment =
                paymentSummaries(orderIds)
                        .get(order.getId());

        java.math.BigDecimal refundedAmount =
                refundRepository.refundedAmounts(orderIds)
                        .get(order.getId());

        return CustomerOrderResponse.from(
                order,
                payment,
                refundedAmount
        );
    }

    /**
     * Lấy trạng thái thanh toán chính xác cho từng đơn.
     * Ưu tiên giao dịch đã hoàn tất.
     * Giao dịch escrow có quyền ưu tiên cao nhất.
     */
    private Map<Long, PaymentTransaction> paymentSummaries(
            List<Long> orderIds
    ) {

        Map<Long, PaymentTransaction> summaries =
                new HashMap<>();

        if (orderIds.isEmpty()) {
            return summaries;
        }

        List<PaymentTransaction> transactions =
                paymentRepository
                    .findByOrderIdInAndIsActiveTrueOrderByCreatedAtDescIdDesc(
                            orderIds
                    );

        for (PaymentTransaction transaction : transactions) {

            summaries.merge(
                    transaction.getOrderId(),
                    transaction,
                    (current, candidate) ->
                            isFinalPayment(current)
                                ? current
                                : isFinalPayment(candidate)
                                    ? candidate
                                    : current
            );
        }

        // Thanh toán gắn với escrow được ưu tiên
        List<PaymentTransaction> escrowPayments =
                paymentRepository
                    .findEscrowPaymentsForOrders(orderIds);

        for (PaymentTransaction escrowPayment : escrowPayments) {
            summaries.put(
                    escrowPayment.getOrderId(),
                    escrowPayment
            );
        }

        return summaries;
    }

    /**
     * Kiểm tra giao dịch đã có kết quả cuối cùng.
     */
    private static boolean isFinalPayment(
            PaymentTransaction payment
    ) {

        return payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.REFUNDED;
    }

    /**
     * Chuyển trạng thái từ request sang OrderStatus.
     */
    private static OrderStatus parseStatus(String status) {

        if (status == null || status.isBlank()) {
            return null;
        }

        try {

            return OrderStatus.valueOf(
                    status.trim().toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trạng thái không hợp lệ"
            );
        }
    }
}