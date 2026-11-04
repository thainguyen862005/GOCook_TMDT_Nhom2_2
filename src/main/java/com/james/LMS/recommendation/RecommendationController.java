package com.james.LMS.recommendation;

import com.james.LMS.config.SecurityUserDetails;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer/recommendations")
@PreAuthorize("hasAuthority('ROLE_USER') and !hasAnyAuthority('ROLE_INSTRUCTOR','ROLE_SYSTEM_ADMIN','ROLE_COMPANY_ADMIN')")
public class RecommendationController {
  private final RecommendationService service;
  private final KnnConfig config;

  public RecommendationController(RecommendationService service, KnnConfig config) {
    this.service = service;
    this.config = config;
  }

  @GetMapping
  public List<RecommendationService.RecommendedMenu> list(
      @AuthenticationPrincipal SecurityUserDetails actor,
      @RequestParam(defaultValue="3") int size,
      @RequestParam(defaultValue="5") int k) {
    if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    if (size < 1 || size > 12 || k < 1 || k > config.maxNeighbors()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size: 1-12; invalid k");
    }
    return service.recommend(actor.getId(), size, k);
  }
}
