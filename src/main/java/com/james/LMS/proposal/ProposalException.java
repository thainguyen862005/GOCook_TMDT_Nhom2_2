package com.james.LMS.proposal;

import com.james.LMS.enums.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ProposalException extends RuntimeException {
  private final HttpStatus status;
  private final ErrorCode errorCode;

  public ProposalException(HttpStatus status, ErrorCode errorCode, String message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }

  /** For ProposalRequestPort implementations: the request id does not exist. */
  public static ProposalException requestNotFound(Long requestId) {
    return new ProposalException(
        HttpStatus.NOT_FOUND,
        ErrorCode.PROPOSAL_REQUEST_NOT_FOUND,
        "Không tìm thấy yêu cầu " + requestId + ".");
  }
}
