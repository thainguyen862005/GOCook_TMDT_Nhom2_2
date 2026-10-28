package com.james.LMS.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CustomerAddressException extends ResponseStatusException {
  public CustomerAddressException(HttpStatus status, String reason) {
    super(status, reason);
  }
}
