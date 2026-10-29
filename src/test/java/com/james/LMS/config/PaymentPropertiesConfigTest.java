package com.james.LMS.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class PaymentPropertiesConfigTest {
  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  @AfterAll
  static void closeFactory() {
    FACTORY.close();
  }

  @Test
  void rejectsLiteralPlaceholderInsteadOfVnpayApiUrl() {
    PaymentProperties properties = validProperties();
    properties.getVnpay().setApiUrl("${VNPAY_API_URL}");

    assertThat(VALIDATOR.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("vnpay.gatewayUrlsValid");
  }

  @Test
  void rejectsUnresolvedMerchantSecrets() {
    PaymentProperties properties = validProperties();
    properties.getVnpay().setHashSecret("${VNPAY_HASH_SECRET}");

    assertThat(VALIDATOR.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("vnpay.merchantCredentialsValid");
  }

  @Test
  void acceptsOfficialSandboxEndpointsAndConfiguredCredentials() {
    assertThat(VALIDATOR.validate(validProperties())).isEmpty();
  }

  private static PaymentProperties validProperties() {
    PaymentProperties properties = new PaymentProperties();
    properties.setFrontendBaseUrl("http://localhost:5173");
    PaymentProperties.Vnpay vnpay = new PaymentProperties.Vnpay();
    vnpay.setTmnCode("TESTTMN1");
    vnpay.setHashSecret("TEST_HASH_SECRET_NOT_REAL");
    vnpay.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
    vnpay.setApiUrl("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
    properties.setVnpay(vnpay);
    return properties;
  }
}
