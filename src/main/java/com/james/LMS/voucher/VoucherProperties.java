package com.james.LMS.voucher;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gocook.voucher")
public class VoucherProperties {

  /** Redemptions (RESERVED or CONSUMED) one customer may hold per voucher. */
  private int perCustomerLimit = 1;
}
