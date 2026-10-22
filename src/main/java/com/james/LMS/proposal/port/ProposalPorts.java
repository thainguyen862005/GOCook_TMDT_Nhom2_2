package com.james.LMS.proposal.port;

import com.james.LMS.enums.ErrorCode;
import com.james.LMS.proposal.ProposalException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Finds the ports implemented by other modules. The application starts without them; a call that
 * needs a missing port fails with HTTP 501 and names the port.
 */
@Component
public class ProposalPorts {

  private final ObjectProvider<ProposalRequestPort> requestPorts;
  private final ObjectProvider<BookingPort> bookingPorts;

  public ProposalPorts(
      ObjectProvider<ProposalRequestPort> requestPorts, ObjectProvider<BookingPort> bookingPorts) {
    this.requestPorts = requestPorts;
    this.bookingPorts = bookingPorts;
  }

  public ProposalRequestPort requests() {
    return single(requestPorts.orderedStream().toList(), "ProposalRequestPort");
  }

  public BookingPort bookings() {
    return single(bookingPorts.orderedStream().toList(), "BookingPort");
  }

  private static <T> T single(List<T> found, String name) {
    if (found.isEmpty()) {
      throw new ProposalException(
          HttpStatus.NOT_IMPLEMENTED,
          ErrorCode.PROPOSAL_PORT_NOT_INTEGRATED,
          "Chưa tích hợp: Thái cần triển khai cổng " + name + ".");
    }
    if (found.size() > 1) {
      throw new IllegalStateException("More than one " + name + " bean");
    }
    return found.get(0);
  }
}
