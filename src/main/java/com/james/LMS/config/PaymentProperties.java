package com.james.LMS.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

  @NotBlank private String frontendBaseUrl;

  @Valid @NotNull private Vnpay vnpay = new Vnpay();

  @Getter
  @Setter
  public static class Vnpay {
    @NotBlank private String tmnCode;
    @NotBlank private String hashSecret;
    @NotBlank private String payUrl;
    @NotBlank private String apiUrl;
  }
}
