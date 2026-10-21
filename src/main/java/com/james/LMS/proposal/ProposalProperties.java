package com.james.LMS.proposal;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "proposal")
public class ProposalProperties {

  /** How far in the future a proposal may expire, counted from the moment the chef saves it. */
  private Duration maxExpiry = Duration.ofDays(14);
}
