package com.james.LMS.enums;

/** Why a payment attempt ended without SUCCESS. */
public enum PaymentCloseReason {
  USER_CANCELLED,
  GATEWAY_FAILED,
  EXPIRED
}
