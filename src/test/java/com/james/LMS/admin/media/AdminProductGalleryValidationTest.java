package com.james.LMS.admin.media;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.james.LMS.admin.dto.request.ProductRequest;
import com.james.LMS.admin.operations.AdminAuditService;
import com.james.LMS.admin.service.AdminProductService;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.repository.ProductImageRepository;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.service.CloudinaryService;
import com.james.LMS.service.impl.ProductImageCleanupService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminProductGalleryValidationTest {
  private AdminProductGalleryService service() {
    return new AdminProductGalleryService(
        mock(AdminProductService.class), mock(ProductRepository.class),
        mock(ProductImageRepository.class), mock(CloudinaryService.class),
        mock(ProductImageCleanupService.class), mock(AdminAuditService.class));
  }

  private ProductRequest product() {
    return new ProductRequest("Mâm cơm", "Món miền Nam", new BigDecimal("100000"),
        2, 8, "Canh rau", "Mô tả", null, ProductStatus.DRAFT);
  }

  @Test void rejectsMoreThanTenPhotosBeforeAnyDatabaseChanges() {
    var keys = Collections.nCopies(11, "new:0");
    assertThatThrownBy(() -> service().save(null,
        new AdminGalleryRequest(product(), keys, "new:0", keys, null), List.of(), 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test void rejectsPublicMenuWithNoCoverBeforeAnyDatabaseChanges() {
    var input = new ProductRequest("Mâm cơm", "Món miền Nam", new BigDecimal("100000"),
        2, 8, "Canh rau", "Mô tả", null, ProductStatus.PUBLISHED);
    assertThatThrownBy(() -> service().save(null,
        new AdminGalleryRequest(input, List.of(), "", List.of(), null), List.of(), 1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ảnh");
  }
}
