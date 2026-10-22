package com.james.LMS.payment;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Error mapping for the seller finance, admin payment read and admin refund controllers only. */
@RestControllerAdvice(
    basePackages = {
      "com.james.LMS.payment.finance",
      "com.james.LMS.payment.adminread",
      "com.james.LMS.refund"
    })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentReadExceptionHandler {

  @ExceptionHandler(PaymentException.class)
  public ResponseEntity<?> payment(PaymentException error) {
    return body(error.getStatus(), error.getErrorCode(), error.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    ConstraintViolationException.class
  })
  public ResponseEntity<?> badParameter(Exception error, HttpServletRequest request) {
    ErrorCode code =
        request.getRequestURI().startsWith("/api/admin")
            ? ErrorCode.ADMIN_PAYMENT_INVALID_PARAM
            : ErrorCode.FINANCE_INVALID_PARAM;
    return body(HttpStatus.BAD_REQUEST, code, "Tham số truy vấn không hợp lệ (ngày dùng định dạng yyyy-MM-dd).");
  }

  private ResponseEntity<?> body(HttpStatus status, ErrorCode code, String message) {
    return ResponseEntity.status(status)
        .body(Map.of("success", false, "errorCode", code.getCode(), "message", message));
  }
}
