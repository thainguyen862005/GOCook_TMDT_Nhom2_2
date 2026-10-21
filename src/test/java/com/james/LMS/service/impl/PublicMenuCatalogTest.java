package com.james.LMS.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.james.LMS.entity.Product;
import com.james.LMS.entity.ProductImage;
import com.james.LMS.entity.User;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.repository.ProductImageRepository;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PublicMenuCatalogTest {
  @Mock ProductRepository products;
  @Mock ProductImageRepository images;
  @Mock UserRepository users;
  @InjectMocks ProductServiceImpl service;

  @Test
  void catalogQueriesPublishedMenusAndBuildsGalleryCoversInBatches() {
    Product menu = Product.builder().id(201L).name("Canh chua")
        .category("Món miền Nam").sellerId(21L).dishItems("Canh chua\nCá kho")
        .unitPrice(new BigDecimal("120000.00")).minGuests(2).maxGuests(6)
        .status(ProductStatus.PUBLISHED).build();
    ProductImage cover = ProductImage.builder().productId(201L)
        .imageUrl("https://res.cloudinary.com/demo/image/upload/cover.jpg")
        .cover(true).displayOrder(0).build();
    User chef = User.builder().id(21L).username("Bếp Demo").build();
    when(products.findPublicMenus(eq(ProductStatus.PUBLISHED), eq("canh"), eq(""),
        isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(menu), PageRequest.of(0, 12), 1));
    when(users.findAllById(any())).thenReturn(List.of(chef));
    when(images.findActiveForProducts(List.of(201L))).thenReturn(List.of(cover));

    var result = service.listPublished(" canh ", "", null, null, 0, 12, "newest");
    assertEquals(1, result.getTotalElements());
    assertEquals(201L, result.getContent().getFirst().id());
    assertEquals("Bếp Demo", result.getContent().getFirst().sellerName());
    assertEquals(cover.getImageUrl(), result.getContent().getFirst().imageUrl());
    verify(products).findPublicMenus(eq(ProductStatus.PUBLISHED), eq("canh"), eq(""),
        isNull(), isNull(), any(Pageable.class));
    verify(users, times(1)).findAllById(any());
    verify(images, times(1)).findActiveForProducts(any());
  }

  @Test
  void invalidPriceRangeDoesNotReachDatabase() {
    assertThrows(IllegalArgumentException.class,
        () -> service.listPublished("", "", new BigDecimal("200000"),
            new BigDecimal("100000"), 0, 12, "newest"));
    verifyNoInteractions(products, images, users);
  }

  @Test
  void invalidPageSizeIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> service.listPublished("", "", null, null, -1, 12, "newest"));
    assertThrows(IllegalArgumentException.class,
        () -> service.listPublished("", "", null, null, 0, 1000, "newest"));
    verifyNoInteractions(products, images, users);
  }

  @Test
  void emptyPageReturnsWithoutLoadingAssociations() {
    when(products.findPublicMenus(eq(ProductStatus.PUBLISHED), eq(""), eq(""),
        isNull(), isNull(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    var result = service.listPublished("", "", null, null, 0, 12, "newest");
    assertTrue(result.isEmpty());
    verifyNoInteractions(images, users);
  }
}
