package com.james.LMS.refund;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Fail closed: never invent a bank refund reference when no real provider is integrated. */
@Component
@ConditionalOnProperty(name = "gocook.refund.simulation-enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableRefundGateway implements RefundGateway {
  @Override
  public RefundOutcome refund(RefundRequest request) {
    return RefundOutcome.failure("Chưa cấu hình cổng hoàn tiền thực. Không chuyển tiền, cần Admin đối soát.");
  }
}
