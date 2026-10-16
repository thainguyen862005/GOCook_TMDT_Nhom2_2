package com.james.LMS.service;

import com.james.LMS.request.PaymentCheckoutRequest;
import com.james.LMS.response.PaymentCheckoutResponse;
import com.james.LMS.response.PaymentIpnResponse;
import com.james.LMS.response.PaymentStatusResponse;
import java.util.Map;

public interface PaymentService {

  PaymentCheckoutResponse checkout(
      Long customerId, String customerEmail, PaymentCheckoutRequest request, String ipAddress);

  PaymentStatusResponse status(Long customerId, String txnRef, String ipAddress);

  PaymentIpnResponse handleVnpayIpn(Map<String, String> params);
}
