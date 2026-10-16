package com.james.LMS.interceptor;

import com.james.LMS.controller.PaymentController;
import com.james.LMS.exception.PaymentException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = PaymentController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentExceptionHandler {

  @ExceptionHandler(PaymentException.class)
  public ResponseEntity<?> payment(PaymentException error) {
    return body(error.getStatus(), error.getErrorCode().getCode(), error.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    ConstraintViolationException.class
  })
  public ResponseEntity<?> validation(Exception error) {
    return body(
        HttpStatus.BAD_REQUEST,
        "PAYMENT_VALIDATION",
        "Thông tin đặt lịch chưa hợp lệ. Kiểm tra họ tên, số điện thoại, địa chỉ và thời gian.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> conflict(DataIntegrityViolationException error) {
    return body(
        HttpStatus.CONFLICT,
        "PAYMENT_CONFLICT",
        "Không thể tạo đơn lúc này. Vui lòng thử lại.");
  }

  private ResponseEntity<?> body(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .body(Map.of("success", false, "errorCode", code, "message", message));
  }
}
