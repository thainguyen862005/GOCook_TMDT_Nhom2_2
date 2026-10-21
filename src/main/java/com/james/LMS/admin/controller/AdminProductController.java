package com.james.LMS.admin.controller;

import com.james.LMS.admin.dto.request.ProductRequest;
import com.james.LMS.admin.dto.response.AdminProductsResponse;
import com.james.LMS.entity.Product;
import com.james.LMS.admin.service.AdminProductService;
import java.util.Map;
import com.james.LMS.config.SecurityUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.james.LMS.admin.operations.AdminAuditService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class AdminProductController {

  private final AdminProductService productService;
  private final AdminAuditService audit;

  @GetMapping
  public AdminProductsResponse search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    Page<Product> result = productService.search(q, category, status, page, size);
    return AdminProductsResponse.from(result, productService.categories(), productService.statuses());
  }

  @GetMapping("/options")
  public Map<String, Object> options() {
    return Map.of("categories", productService.categories(), "statuses", productService.statuses());
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable Long id) {
    return Map.of(
        "product", productService.get(id),
        "categories", productService.categories(),
        "statuses", productService.statuses());
  }

  @PostMapping
  @Transactional
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@RequestBody ProductRequest request, @AuthenticationPrincipal SecurityUserDetails actor) {
    Product product = productService.create(
        request.name(), request.category(), request.unitPrice(), request.minGuests(), request.maxGuests(),
        request.dishItems(), request.description(), request.imageUrl(), request.status());
    audit.record(actor.getId(), "PRODUCT_CREATE", "PRODUCT", product.getId(), "Tạo thực đơn");
    return Map.of("message", "Đã thêm thực đơn mới.", "product", product);
  }

  @PutMapping("/{id}")
  @Transactional
  public Map<String, Object> update(@PathVariable Long id, @RequestBody ProductRequest request, @AuthenticationPrincipal SecurityUserDetails actor) {
    productService.update(
        id, request.name(), request.category(), request.unitPrice(), request.minGuests(), request.maxGuests(),
        request.dishItems(), request.description(), request.imageUrl(), request.status());
    audit.record(actor.getId(), "PRODUCT_UPDATE", "PRODUCT", id, "Cập nhật thực đơn");
    return Map.of("message", "Đã cập nhật thực đơn.", "product", productService.get(id));
  }

  @PatchMapping("/{id}/toggle")
  @Transactional
  public Map<String, Object> toggle(@PathVariable Long id, @AuthenticationPrincipal SecurityUserDetails actor) {
    productService.toggleVisibility(id);
    audit.record(actor.getId(), "PRODUCT_TOGGLE", "PRODUCT", id, "Ẩn hoặc hiển thị thực đơn");
    return Map.of("message", "Đã cập nhật trạng thái hiển thị.", "product", productService.get(id));
  }
}
