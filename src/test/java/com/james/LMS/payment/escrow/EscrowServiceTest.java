package com.james.LMS.payment.escrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.exception.PaymentException;
import com.james.LMS.payment.escrow.EscrowService.ReleaseResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class EscrowServiceTest {
  static final long ORDER = 8L;
  static final long CUSTOMER = 5L;

  final EscrowRepository escrows = mock(EscrowRepository.class);
  final EscrowService service =
      new EscrowService(escrows, new TransactionTemplate(mock(PlatformTransactionManager.class)));

  EscrowServiceTest() {
    when(escrows.lockOrderCustomer(ORDER)).thenReturn(Optional.of(CUSTOMER));
  }

  void assertRejected(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            PaymentException.class, error -> assertThat(error.getErrorCode()).isEqualTo(expected));
  }

  static RuntimeException dbError(String message) {
    return new RuntimeException("could not execute query", new IllegalStateException("ERROR: " + message));
  }

  @Test
  void theCustomerConfirmingAnAwaitingOrderReleasesTheMoney() {
    when(escrows.acceptAndRelease(ORDER, CUSTOMER)).thenReturn(true);

    assertThat(service.release(ORDER, CUSTOMER)).isEqualTo(ReleaseResult.RELEASED);
  }

  @Test
  void confirmingTwiceAnswersAlreadyReleasedAndSkipsTheDisputeCheck() {
    when(escrows.acceptAndRelease(ORDER, CUSTOMER)).thenReturn(true, false);

    service.release(ORDER, CUSTOMER);
    when(escrows.isReleased(ORDER)).thenReturn(true);
    when(escrows.hasOpenDispute(ORDER)).thenReturn(true);

    assertThat(service.release(ORDER, CUSTOMER)).isEqualTo(ReleaseResult.ALREADY_RELEASED);
  }

  @Test
  void anOpenDisputeBlocksTheReleaseBeforeTheFunctionRuns() {
    when(escrows.hasOpenDispute(ORDER)).thenReturn(true);

    assertRejected(() -> service.release(ORDER, CUSTOMER), ErrorCode.ESCROW_DISPUTE_OPEN);
    verify(escrows, never()).acceptAndRelease(any(), any());
  }

  @Test
  void onlyTheCustomerOfTheOrderCanConfirm() {
    assertRejected(() -> service.release(ORDER, 99L), ErrorCode.ESCROW_NOT_OWNER);
    verify(escrows, never()).acceptAndRelease(any(), any());
  }

  @Test
  void anUnknownOrderIsNotFound() {
    assertRejected(() -> service.release(404L, CUSTOMER), ErrorCode.ESCROW_ORDER_NOT_FOUND);
  }

  @Test
  void theMessagesOfTheDatabaseFunctionBecomeCodedErrors() {
    when(escrows.acceptAndRelease(ORDER, CUSTOMER))
        .thenThrow(dbError("Order must await acceptance, actual: PENDING"))
        .thenThrow(dbError("No HELD escrow for order 8"))
        .thenThrow(dbError("Customer is not order owner"))
        .thenThrow(dbError("Order 8 not found"));

    assertRejected(() -> service.release(ORDER, CUSTOMER), ErrorCode.ESCROW_ORDER_NOT_AWAITING);
    assertRejected(() -> service.release(ORDER, CUSTOMER), ErrorCode.ESCROW_NOT_HELD);
    assertRejected(() -> service.release(ORDER, CUSTOMER), ErrorCode.ESCROW_NOT_OWNER);
    assertRejected(() -> service.release(ORDER, CUSTOMER), ErrorCode.ESCROW_ORDER_NOT_FOUND);
  }

  @Test
  void anUnexpectedDatabaseErrorIsNotHidden() {
    RuntimeException broken = dbError("deadlock detected");
    when(escrows.acceptAndRelease(ORDER, CUSTOMER)).thenThrow(broken);

    assertThatThrownBy(() -> service.release(ORDER, CUSTOMER)).isSameAs(broken);
  }
}
