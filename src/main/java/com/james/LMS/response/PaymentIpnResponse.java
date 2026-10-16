package com.james.LMS.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentIpnResponse(
    @JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {

  public static PaymentIpnResponse confirmed() {
    return new PaymentIpnResponse("00", "Confirm Success");
  }

  public static PaymentIpnResponse orderNotFound() {
    return new PaymentIpnResponse("01", "Order not Found");
  }

  public static PaymentIpnResponse alreadyConfirmed() {
    return new PaymentIpnResponse("02", "Order already confirmed");
  }

  public static PaymentIpnResponse invalidAmount() {
    return new PaymentIpnResponse("04", "Invalid Amount");
  }

  public static PaymentIpnResponse invalidChecksum() {
    return new PaymentIpnResponse("97", "Invalid Checksum");
  }

  public static PaymentIpnResponse unknownError() {
    return new PaymentIpnResponse("99", "Unknown error");
  }
}
