package com.james.LMS.proposal;

import com.james.LMS.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Optimistic locking comes from {@link BaseEntity#version} (@Version). */
@Entity
@Table(name = "proposals")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Proposal extends BaseEntity {

  @Column(name = "custom_request_id", nullable = false)
  private Long customRequestId;

  @Column(name = "chef_id", nullable = false)
  private Long chefId;

  @Column(name = "proposed_price", nullable = false, precision = 14, scale = 2)
  private BigDecimal proposedPrice;

  @Column(name = "dishes", columnDefinition = "TEXT")
  private String dishes;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "revision", nullable = false)
  @Builder.Default
  private Integer revision = 1;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  @Builder.Default
  private ProposalStatus status = ProposalStatus.DRAFT;

  @Column(name = "accepted_booking_id")
  private Long acceptedBookingId;

  @Column(name = "accepted_order_code", length = 50)
  private String acceptedOrderCode;

  /** Status as the customer should see it: SENT/REVISED past expiresAt count as EXPIRED. */
  public ProposalStatus statusAt(OffsetDateTime now) {
    boolean live = status == ProposalStatus.SENT || status == ProposalStatus.REVISED;
    return live && expiresAt != null && expiresAt.isBefore(now) ? ProposalStatus.EXPIRED : status;
  }
}
