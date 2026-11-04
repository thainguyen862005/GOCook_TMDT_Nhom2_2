package com.james.LMS.recommendation;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public item-to-item cosine recommendations (never customer personalization). */
@RestController
public class PublicSimilarMenusController {
  private final RecommendationService service;

  public PublicSimilarMenusController(RecommendationService service) {
    this.service = service;
  }

  @GetMapping("/api/public/menus/{menuId}/similar")
  public List<RecommendationService.RecommendedMenu> similar(
      @PathVariable long menuId,
      @RequestParam(defaultValue = "3") int size) {
    if (menuId <= 0 || size < 1 || size > 6) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid menu ID or size");
    }
    return service.similar(menuId, size);
  }
}
