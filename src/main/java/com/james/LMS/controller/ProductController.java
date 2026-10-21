package com.james.LMS.controller;

import com.james.LMS.response.ProductDetailResponse;
import com.james.LMS.response.PublicMenuCardResponse;
import com.james.LMS.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
  private final ProductService service;

  @GetMapping
  public Page<PublicMenuCardResponse> list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size,
      @RequestParam(defaultValue = "newest") String sort,
      @RequestParam(required = false) Long chef) {
    return chef == null ? service.listPublished(q, category, minPrice, maxPrice, page, size, sort)
        : service.listPublishedForChef(chef, q, category, minPrice, maxPrice, page, size, sort);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> badCatalogRequest(IllegalArgumentException error) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
        "success", false, "errorCode", "MENU_FILTER_INVALID", "message", error.getMessage()));
  }

  @GetMapping("/categories")
  public List<String> categories() {
    return service.publicCategories();
  }

  @GetMapping("/{id}")
  public ProductDetailResponse get(@PathVariable Long id) {
    return service.getPublished(id);
  }
}
