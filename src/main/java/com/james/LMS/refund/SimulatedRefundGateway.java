package com.james.LMS.refund;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Simulated like the escrow itself: no real money moves. A real VNPay refund is out of scope. */
@Component
@ConditionalOnProperty(name="gocook.refund.simulation-enabled",havingValue="true")
public class SimulatedRefundGateway implements RefundGateway {

  @Override
  public RefundOutcome refund(RefundRequest request) {
    return RefundOutcome.success("SIM-" + request.refundId());
  }
}
