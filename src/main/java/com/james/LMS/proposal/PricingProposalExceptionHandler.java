package com.james.LMS.proposal;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Error mapping for the pricing and proposal controllers only. */
@RestControllerAdvice(basePackages = {"com.james.LMS.pricing", "com.james.LMS.proposal"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PricingProposalExceptionHandler {

  @ExceptionHandler(ProposalException.class)
  public ResponseEntity<?> proposal(ProposalException error) {
    return body(error.getStatus(), error.getErrorCode(), error.getMessage());
  }

  @ExceptionHandler(PaymentException.class)
  public ResponseEntity<?> pricing(PaymentException error) {
    return body(error.getStatus(), error.getErrorCode(), error.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class
  })
  public ResponseEntity<?> validation(Exception error) {
    return body(
        HttpStatus.BAD_REQUEST,
        ErrorCode.PROPOSAL_VALIDATION,
        "Dữ liệu gửi lên chưa hợp lệ. Kiểm tra lại các trường bắt buộc.");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<?> version(OptimisticLockingFailureException error) {
    return body(
        HttpStatus.CONFLICT,
        ErrorCode.PROPOSAL_VERSION_CONFLICT,
        "Báo giá vừa được thay đổi. Hãy tải lại rồi thử lại.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> conflict(DataIntegrityViolationException error) {
    return body(
        HttpStatus.CONFLICT,
        ErrorCode.PROPOSAL_CONFLICT,
        "Không thể lưu báo giá lúc này. Vui lòng thử lại.");
  }

  private ResponseEntity<?> body(HttpStatus status, ErrorCode code, String message) {
    return ResponseEntity.status(status)
        .body(Map.of("success", false, "errorCode", code.getCode(), "message", message));
  }
}
