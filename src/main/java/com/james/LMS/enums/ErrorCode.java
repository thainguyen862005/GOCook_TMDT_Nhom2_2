package com.james.LMS.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  USER_NOT_FOUND("1000", "User not found exception"),
  PERMISSION_DENIES("1001", "Permission denied exception"),
  ROLE_NOT_FOUND("1002", "Role not found"),
  JWT_INVALID("1003", "Jwt invalid"),
  NOT_MATCHED_PASSWORD("1004", "Password doesn't match"),
  USER_ALREADY_EXISTS("1005", "User already exists"),
  SPAM_FORGOT_PASSWORD("1006", "Spam forgot password"),
  OTP_TIMEOUT("1007", "OTP timeout"),
  NOT_MATCHED_OTP("1008", "OPT doesn't match"),
  INSTRUCTOR_ALREADY_EXISTS("1009", "Instructor already exists"),
  FILE_ERROR_UPLOAD("1010", "Upload error"),
  COMPANY_ADMIN_ROLE_ALREADY_EXISTS("1011", "Company role already exists"),
  INVALID_CREDENTIALS("1012", "Email or password is incorrect"),
  EMAIL_NOT_VERIFIED("1013", "Email has not been verified"),
  EMAIL_ALREADY_VERIFIED("1014", "Email has already been verified"),
  SPAM_EMAIL_VERIFICATION("1015", "Please wait before requesting another verification code"),
  PAYMENT_PRODUCT_UNAVAILABLE("1016", "Product is not available for payment"),
  PAYMENT_INVALID_GUESTS("1017", "Guest count is outside the product limits"),
  PAYMENT_INVALID_SCHEDULE("1018", "Scheduled time must be in the future"),
  PAYMENT_NOT_FOUND("1019", "Payment transaction not found"),
  PAYMENT_GATEWAY_UNAVAILABLE("1020", "Payment gateway request failed");

  private final String code;
  private final String message;
}
