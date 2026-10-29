package com.james.LMS.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Locale;
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

    // A placeholder like ${VNPAY_API_URL} passes @NotBlank, but crashes URI.create()
    // during querydr. Reject it at startup rather than during payment.
    @AssertTrue(message = "VNPAY_PAY_URL và VNPAY_API_URL phải là URL HTTPS hợp lệ, không phải ${...}.")
    public boolean isGatewayUrlsValid() {
      return isHttpsUrl(payUrl) && isHttpsUrl(apiUrl);
    }

    @AssertTrue(message = "VNPAY_TMN_CODE và VNPAY_HASH_SECRET phải được cấu hình bằng giá trị Sandbox thật.")
    public boolean isMerchantCredentialsValid() {
      return isConfigured(tmnCode) && isConfigured(hashSecret);
    }

    private static boolean isConfigured(String value) {
      return value != null && !value.isBlank() && !value.contains("${");
    }

    private static boolean isHttpsUrl(String value) {
      if (!isConfigured(value)) {
        return false;
      }
      try {
        URI uri = URI.create(value.trim());
        return "https".equals(uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT))
            && uri.getHost() != null
            && !uri.getHost().isBlank()
            && uri.getRawUserInfo() == null
            && uri.getRawFragment() == null;
      } catch (IllegalArgumentException e) {
        return false;
      }
    }
  }
}
