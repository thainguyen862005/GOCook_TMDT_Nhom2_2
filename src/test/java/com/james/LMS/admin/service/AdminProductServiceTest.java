package com.james.LMS.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.james.LMS.entity.Product;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {
  @Mock ProductRepository products;
  @InjectMocks AdminProductService service;

  @Test
  void rejectsPublishedNewMenuWithoutChef() {
    assertThrows(IllegalArgumentException.class, () -> create("120000", 2, 6,
        "/assets/meal.png", ProductStatus.PUBLISHED));
    verify(products, never()).save(any());
  }

  @Test
  void rejectsInvalidGuestRange() {
    assertThrows(IllegalArgumentException.class, () -> create("120000", 9, 3,
        "/assets/meal.png", ProductStatus.DRAFT));
  }

  @Test
  void rejectsNegativePrice() {
    assertThrows(IllegalArgumentException.class, () -> create("-100", 2, 8,
        null, ProductStatus.DRAFT));
  }

  @Test
  void cannotPublishOrphanProduct() {
    Product orphan = Product.builder().name("Draft").unitPrice(new BigDecimal("120000"))
        .status(ProductStatus.DRAFT).build();
    when(products.findById(5L)).thenReturn(Optional.of(orphan));
    assertThrows(IllegalArgumentException.class, () -> service.toggleVisibility(5L));
    verify(products, never()).save(any());
  }

  private void create(String price, int min, int max, String image, ProductStatus status) {
    service.create("Menu A01", "Món miền Nam", new BigDecimal(price), min, max,
        "Canh chua", "Mâm cơm tại nhà", image, status);
  }
}
