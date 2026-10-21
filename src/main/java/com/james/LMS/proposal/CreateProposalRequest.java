package com.james.LMS.proposal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** {@code send} true sends right away (SENT); false or missing keeps a DRAFT. */
public record CreateProposalRequest(
    @NotNull BigDecimal proposedPrice,
    @Size(max = 4000) String dishes,
    @Size(max = 4000) String note,
    @NotNull OffsetDateTime expiresAt,
    Boolean send) {}
