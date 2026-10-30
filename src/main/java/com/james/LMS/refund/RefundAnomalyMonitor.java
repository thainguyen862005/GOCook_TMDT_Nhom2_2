package com.james.LMS.refund;

import com.james.LMS.notification.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically queues only proven duplicate captures. Other ambiguous payments need human reconciliation. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundAnomalyMonitor {
  private final JdbcTemplate jdbc;
  private final RefundService refunds;
  private final NotificationService notifications;

  @Scheduled(fixedDelayString="${gocook.refund.monitor-interval-ms:600000}",initialDelayString="${gocook.refund.monitor-initial-delay-ms:60000}")
  public void scan() {
    try {
      List<Long> proven=jdbc.queryForList("""
        SELECT p.id FROM payment_transactions p
        JOIN order_escrows e ON e.order_id=p.order_id
        WHERE p.status='SUCCESS' AND p.id<>e.payment_transaction_id
          AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.payment_transaction_id=p.id)
        ORDER BY p.id LIMIT 30
        """,Long.class);
      for(Long paymentId: proven) {
        try {
          Refund refund=refunds.queueDuplicate(paymentId);
          notifications.sendToAdmins("refund:duplicate:"+paymentId,"PAYMENT",
              "Phát hiện thanh toán trùng", "Refund #"+refund.id()+" đang chờ đối soát và chuyển khoản hoàn.",
              "/admin/manage/payments");
        } catch(Exception exception) {
          log.warn("Auto-refund queue requires manual review for payment {}: {}",paymentId,exception.getMessage());
        }
      }
      // Payment SUCCESS without escrow is not automatically refunded: it may be mid-settlement.
      // Alert Admin and reconcile first rather than accidentally refunding a valid payment.
      List<Long> unresolved=jdbc.queryForList("""
        SELECT p.id FROM payment_transactions p LEFT JOIN order_escrows e ON e.order_id=p.order_id
        WHERE p.status='SUCCESS' AND e.order_id IS NULL
        ORDER BY p.id LIMIT 30
        """,Long.class);
      for(Long paymentId:unresolved)
        notifications.sendToAdmins("refund:unresolved:"+paymentId,"PAYMENT",
          "Thanh toán cần đối soát", "Payment #"+paymentId+" thành công nhưng chưa có escrow. Không tự hoàn khi chưa xác minh.",
          "/admin/manage/payments");
    } catch(Exception exception) {
      log.warn("Refund reconciliation scan unavailable: {}",exception.getMessage());
    }
  }
}
