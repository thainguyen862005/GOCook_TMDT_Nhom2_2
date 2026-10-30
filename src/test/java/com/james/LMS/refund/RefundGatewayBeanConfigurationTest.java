package com.james.LMS.refund;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Guards against startup failure when the simulated refund gateway is disabled. */
class RefundGatewayBeanConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(GatewayTestConfig.class);

  @Configuration(proxyBeanMethods = false)
  @Import({UnavailableRefundGateway.class, SimulatedRefundGateway.class})
  static class GatewayTestConfig {}

  @Test
  void missingFlagUsesUnavailableGateway() {
    runner.run(context -> {
      assertThat(context).hasSingleBean(RefundGateway.class);
      assertThat(context.getBean(RefundGateway.class))
          .isInstanceOf(UnavailableRefundGateway.class);
    });
  }

  @Test
  void falseFlagUsesUnavailableGateway() {
    runner.withPropertyValues("gocook.refund.simulation-enabled=false")
        .run(context -> {
          assertThat(context).hasSingleBean(RefundGateway.class);
          assertThat(context.getBean(RefundGateway.class))
              .isInstanceOf(UnavailableRefundGateway.class);
        });
  }

  @Test
  void trueFlagUsesSimulatedGateway() {
    runner.withPropertyValues("gocook.refund.simulation-enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(RefundGateway.class);
          assertThat(context.getBean(RefundGateway.class))
              .isInstanceOf(SimulatedRefundGateway.class);
        });
  }
}
