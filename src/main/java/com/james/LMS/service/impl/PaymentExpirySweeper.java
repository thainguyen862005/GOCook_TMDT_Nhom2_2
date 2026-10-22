package com.james.LMS.service.impl;

import com.james.LMS.service.PaymentService;
import com.james.LMS.service.PaymentService.SweepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes abandoned payment attempts and unpaid orders so they do not hold voucher uses forever. The
 * work is the same code status() runs lazily. Switch off with gocook.payment.sweep-enabled=false
 * (tests do).
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "gocook.payment.sweep-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PaymentExpirySweeper {

  static final int MAX_PER_RUN = 50;

  private final PaymentService payments;

  @Scheduled(fixedDelayString = "${gocook.payment.sweep-interval:PT1M}")
  public void sweep() {
    SweepResult result = payments.sweepExpired(MAX_PER_RUN);
    if (result.attempts() > 0 || result.orders() > 0) {
      log.info("payment sweep attempts={} orders={}", result.attempts(), result.orders());
    }
  }
}
