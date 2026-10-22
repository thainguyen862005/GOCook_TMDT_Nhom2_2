package com.james.LMS.proposal;

import java.math.BigDecimal;
import java.util.List;

public record ProposalList(
    Long requestId, BigDecimal budgetMin, BigDecimal budgetMax, int count, List<ProposalCard> items) {}
