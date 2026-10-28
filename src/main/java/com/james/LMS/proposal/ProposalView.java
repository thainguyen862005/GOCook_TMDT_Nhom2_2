package com.james.LMS.proposal;

import com.james.LMS.pricing.PriceGuidance.PriceVerdict;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** What the chef gets back from create and update. priceGuidance is information only. */
public record ProposalView(
    Long id,
    Long customRequestId,
    Long chefId,
    BigDecimal proposedPrice,
    String dishes,
    String note,
    OffsetDateTime expiresAt,
    int revision,
    long version,
    ProposalStatus status,
    PriceVerdict priceGuidance) {}
