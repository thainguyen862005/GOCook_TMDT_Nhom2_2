package com.james.LMS.proposal;

import com.james.LMS.proposal.port.BookingPort;
import com.james.LMS.proposal.port.ProposalPorts;
import com.james.LMS.proposal.port.ProposalRequestPort;
import com.james.LMS.proposal.port.ProposalRequestPort.RequestSnapshot;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Fakes of the ports Thái implements; they live in tests only. */
public final class TestPorts {
  private TestPorts() {}

  public static final long CUSTOMER = 1L;
  public static final long CHEF = 40L;
  public static final long OTHER_CHEF = 41L;
  public static final long REQUEST_ID = 5L;

  public static RequestSnapshot snapshot() {
    return new RequestSnapshot(
        REQUEST_ID,
        CUSTOMER,
        true,
        4,
        new BigDecimal("800000"),
        new BigDecimal("1500000"),
        Set.of(CHEF, OTHER_CHEF, 42L),
        "Mon Viet",
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public static RequestSnapshot closed(RequestSnapshot open) {
    return new RequestSnapshot(
        open.requestId(),
        open.customerId(),
        false,
        open.guestCount(),
        open.budgetMin(),
        open.budgetMax(),
        open.matchedChefIds(),
        open.cuisine(),
        open.dishCount(),
        open.dietaryTags(),
        open.allergyTags(),
        open.serviceArea(),
        open.durationMinutes(),
        open.scheduledAt());
  }

  public static class FakeRequestPort implements ProposalRequestPort {
    public final Map<Long, RequestSnapshot> requests = new HashMap<>();

    public FakeRequestPort(RequestSnapshot... initial) {
      for (RequestSnapshot request : initial) {
        requests.put(request.requestId(), request);
      }
    }

    @Override
    public RequestSnapshot load(Long requestId) {
      RequestSnapshot found = requests.get(requestId);
      if (found == null) {
        throw ProposalException.requestNotFound(requestId);
      }
      return found;
    }
  }

  public static class FakeBookingPort implements BookingPort {
    public final List<BookingCommand> calls = new ArrayList<>();
    public final Map<Long, BookingRef> created = new HashMap<>();
    public RuntimeException failure;

    @Override
    public BookingRef createFromProposal(BookingCommand command) {
      calls.add(command);
      if (failure != null) {
        throw failure;
      }
      return created.computeIfAbsent(
          command.proposalId(),
          id -> new BookingRef(500 + id, "GC261021T" + id));
    }
  }

  public static ProposalPorts ports(FakeRequestPort requests, FakeBookingPort bookings) {
    StaticListableBeanFactory beans = new StaticListableBeanFactory();
    if (requests != null) {
      beans.addBean("requests", requests);
    }
    if (bookings != null) {
      beans.addBean("bookings", bookings);
    }
    return new ProposalPorts(
        beans.getBeanProvider(ProposalRequestPort.class), beans.getBeanProvider(BookingPort.class));
  }
}
