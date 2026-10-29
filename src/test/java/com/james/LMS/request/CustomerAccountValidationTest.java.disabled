package com.james.LMS.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class CustomerAccountValidationTest {
  static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
  static final Validator validator = factory.getValidator();

  @AfterAll
  static void close() { factory.close(); }

  @Test
  void acceptsVietnameseAndInternationalPhones() {
    for (String phone : new String[] {"0901234567", "+84 901 234 567", "(invalid)"}) {
      var request = new CustomerAddressRequest("Nhà", "Khách", phone, "12 Đường A", "",
          "Quận 1", "TP.HCM", "", false, null);
      assertThat(validator.validate(request).isEmpty()).isEqualTo(!phone.equals("(invalid)"));
    }
  }

  @Test
  void rejectsWhitespaceOnlyRequiredFieldsAndOversizedNote() {
    var request = new CustomerAddressRequest("  ", " ", " ", " ", "", " ", " ",
        "x".repeat(2001), false, -1L);
    assertThat(validator.validate(request)).hasSizeGreaterThanOrEqualTo(7);
  }

  @Test
  void profileAllowsClearingOptionalFieldsAndRejectsInvalidPhone() {
    var valid = new UpdateUserProfileRequest(null, "Khách", "", "", "", null, null);
    assertThat(validator.validate(valid)).isEmpty();
    var invalid = new UpdateUserProfileRequest(null, "  ", "abc", "x".repeat(501), "", null, null);
    assertThat(validator.validate(invalid)).hasSize(3);
  }

  @Test
  void legacyProfileConstructorStillWorks() {
    var request = new UpdateUserProfileRequest(null, "Khách", null, null);
    assertThat(request.getPhone()).isNull();
    assertThat(validator.validate(request)).isEmpty();
  }
}
