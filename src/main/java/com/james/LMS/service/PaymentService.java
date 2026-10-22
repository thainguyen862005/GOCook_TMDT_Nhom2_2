package com.james.LMS.service;

import com.james.LMS.request.PaymentCheckoutRequest;
import com.james.LMS.request.PaymentOrderCheckoutRequest;
import com.james.LMS.response.PaymentCheckoutResponse;
import com.james.LMS.response.PaymentIpnResponse;
import com.james.LMS.response.PaymentStatusResponse;
import java.util.Map;

public interface PaymentService {

  PaymentCheckoutResponse checkout(
      Long customerId, String customerEmail, PaymentCheckoutRequest request, String ipAddress);

  /** Pays an order that already exists as PENDING (for example the booking of an accepted proposal). */
  PaymentCheckoutResponse checkoutOrder(
      Long customerId, PaymentOrderCheckoutRequest request, String ipAddress);

  PaymentStatusResponse status(Long customerId, String txnRef, String ipAddress);

  PaymentIpnResponse handleVnpayIpn(Map<String, String> params);

  /**
   * The customer's browser comes back from VNPay with signed vnp_* parameters. Only a signed
   * cancel (vnp_ResponseCode 24) acts: it closes the attempt. Anything else is ignored here,
   * success is settled only by IPN or querydr.
   */
  PaymentStatusResponse handleVnpayReturn(Long customerId, Map<String, String> params);

  /** Closes abandoned attempts and orders (at most {@code max} of each); returns what it did. */
  SweepResult sweepExpired(int max);

  record SweepResult(int attempts, int orders) {}
}
