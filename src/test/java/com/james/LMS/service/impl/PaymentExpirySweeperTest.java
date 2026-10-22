package com.james.LMS.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.james.LMS.service.PaymentService;
import com.james.LMS.service.PaymentService.SweepResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PaymentExpirySweeperTest {
  final PaymentService payments = mock(PaymentService.class);

  PaymentExpirySweeperTest() {
    when(payments.sweepExpired(anyInt())).thenReturn(new SweepResult(0, 0));
  }

  @Test
  void eachRunSweepsAtMostFiftyAttemptsAndOrders() {
    when(payments.sweepExpired(50)).thenReturn(new SweepResult(2, 1));

    new PaymentExpirySweeper(payments).sweep();

    verify(payments).sweepExpired(50);
  }

  @Test
  void theSweeperIsOnByDefaultAndTheTestSwitchTurnsItOff() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withBean(PaymentService.class, () -> payments)
            .withUserConfiguration(PaymentExpirySweeper.class);

    runner.run(context -> assertThat(context).hasSingleBean(PaymentExpirySweeper.class));
    runner
        .withPropertyValues("gocook.payment.sweep-enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(PaymentExpirySweeper.class));
  }
}
