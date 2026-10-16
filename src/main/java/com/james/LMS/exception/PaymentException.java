package com.james.LMS.exception;

import com.james.LMS.enums.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PaymentException extends RuntimeException {
  private final HttpStatus status;
  private final ErrorCode errorCode;

  public PaymentException(HttpStatus status, ErrorCode errorCode, String message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }
}
