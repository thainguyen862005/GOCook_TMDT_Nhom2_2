package com.james.LMS.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gocook.payment")
public class PaymentLifecycleProperties {

  /** Extra time after an attempt's expiry before it is closed, so a late IPN can still win. */
  private Duration expiryGrace = Duration.ofMinutes(2);

  /** How long after creation an order may still be paid (counted from orders.created_at). */
  private Duration orderPayWindow = Duration.ofMinutes(30);
}
