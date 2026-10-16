package com.james.LMS.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

public interface PaymentGateway {

  ZoneId GATEWAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  DateTimeFormatter GATEWAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  record PaymentRequest(
      String txnRef,
      BigDecimal amount,
      String orderInfo,
      String returnUrl,
      String ipAddress,
      LocalDateTime createdAt,
      LocalDateTime expiresAt) {}

  enum Outcome {
    SUCCESS,
    FAILED,
    CANCELLED,
    NOT_FOUND,
    UNKNOWN
  }

  record GatewayTransaction(
      String txnRef,
      BigDecimal amount,
      String merchantCode,
      Outcome outcome,
      String transactionNo,
      String payDate,
      String responseCode,
      String transactionStatus) {

    public GatewayTransaction withOutcome(Outcome newOutcome) {
      return new GatewayTransaction(
          txnRef,
          amount,
          merchantCode,
          newOutcome,
          transactionNo,
          payDate,
          responseCode,
          transactionStatus);
    }
  }

  String merchantCode();

  String createPaymentUrl(PaymentRequest request);

  Optional<GatewayTransaction> verifyCallback(Map<String, String> params);

  GatewayTransaction query(String txnRef, String transactionDate, String ipAddress);
}
