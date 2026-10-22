package com.james.LMS;

import static org.assertj.core.api.Assertions.assertThat;

import com.james.LMS.service.impl.PaymentExpirySweeper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class LmsApplicationTests {

  @Autowired ApplicationContext context;

  @Test
  void contextLoads() {}

  @Test
  void theExpirySweeperDoesNotRunInTests() {
    assertThat(context.getBeanNamesForType(PaymentExpirySweeper.class)).isEmpty();
  }
}
