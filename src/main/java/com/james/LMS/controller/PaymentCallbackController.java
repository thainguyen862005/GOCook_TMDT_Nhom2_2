package com.james.LMS.controller;

import com.james.LMS.response.PaymentIpnResponse;
import com.james.LMS.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentCallbackController {
  private final PaymentService service;

  @GetMapping("/vnpay-ipn")
  public PaymentIpnResponse vnpayIpn(HttpServletRequest request) {
    Map<String, String> params = new HashMap<>();
    request.getParameterMap().forEach((name, values) -> params.put(name, values[0]));
    try {
      return service.handleVnpayIpn(params);
    } catch (RuntimeException e) {
      log.error("vnpay ipn failed unexpectedly: {}", e.getClass().getSimpleName(), e);
      return PaymentIpnResponse.unknownError();
    }
  }
}
