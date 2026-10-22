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
  PAYMENT_GATEWAY_UNAVAILABLE("1020", "Payment gateway request failed"),
  PAYMENT_IDEMPOTENCY_CONFLICT("1021", "Idempotency key belongs to another customer"),

  // Pricing / proposal (Sprint 1). Block 1100-1199 belongs to the payment/proposal group.
  PRICING_VOUCHER_NOT_SUPPORTED("1100", "Vouchers are not supported yet"),
  PROPOSAL_REQUEST_NOT_FOUND("1101", "Custom request not found"),
  PROPOSAL_NOT_FOUND("1102", "Proposal not found"),
  PROPOSAL_CHEF_NOT_MATCHED("1103", "Chef is not matched to this request"),
  PROPOSAL_REQUEST_CLOSED("1104", "Request is not accepting proposals"),
  PROPOSAL_INVALID_PRICE("1105", "Proposed price must be greater than zero"),
  PROPOSAL_INVALID_EXPIRY("1106", "Proposal expiry is outside the allowed window"),
  PROPOSAL_ALREADY_EXISTS("1107", "Chef already has a live proposal for this request"),
  PROPOSAL_VERSION_CONFLICT("1108", "Proposal was changed by someone else"),
  PROPOSAL_INVALID_TRANSITION("1109", "Proposal status change is not allowed"),
  PROPOSAL_EXPIRED("1110", "Proposal has expired"),
  PROPOSAL_FORBIDDEN("1111", "Not allowed to access this proposal"),
  PROPOSAL_CHEF_INACTIVE("1112", "Chef is not active"),
  PROPOSAL_ALREADY_RESOLVED("1113", "Another proposal was already accepted"),
  PROPOSAL_BOOKING_UNAVAILABLE("1114", "Booking slot is no longer available"),
  PROPOSAL_PORT_NOT_INTEGRATED("1115", "Required integration port is not implemented yet"),
  PROPOSAL_VALIDATION("1116", "Proposal data is invalid"),
  PROPOSAL_CONFLICT("1117", "Proposal conflicts with existing data"),
  PAYMENT_ORDER_NOT_PAYABLE("1118", "Order cannot be paid right now"),
  PAYMENT_ORDER_WINDOW_PASSED("1119", "The time allowed to pay this order has passed"),

  // Vouchers (Sprint 2). Codes 1120-1129 are reserved for them.
  VOUCHER_NOT_FOUND("1120", "Voucher does not exist"),
  VOUCHER_INACTIVE("1121", "Voucher is not active"),
  VOUCHER_NOT_STARTED("1122", "Voucher is not valid yet"),
  VOUCHER_EXPIRED("1123", "Voucher has expired"),
  VOUCHER_MIN_ORDER("1124", "Order is below the voucher minimum"),
  VOUCHER_EXHAUSTED("1125", "Voucher usage limit has been reached"),
  VOUCHER_CUSTOMER_LIMIT("1126", "Customer already used this voucher the allowed number of times"),
  VOUCHER_COVERS_ORDER("1127", "Voucher would cover the whole order"),
  VOUCHER_CHANGED("1128", "Voucher changed while the order was being created"),

  // Return-URL cancel (Sprint 2). Codes from 1129.
  PAYMENT_RETURN_INVALID_SIGNATURE("1129", "Return parameters are not signed by the gateway"),
  PAYMENT_RETURN_MISMATCH("1130", "Return parameters do not match the payment");

  private final String code;
  private final String message;
}
