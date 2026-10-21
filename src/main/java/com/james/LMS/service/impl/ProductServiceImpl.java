package com.james.LMS.service.impl;

import com.james.LMS.entity.ProductImage;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.exception.SellerProductException;
import com.james.LMS.repository.ProductImageRepository;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.response.ProductDetailResponse;
import com.james.LMS.response.PublicMenuCardResponse;
import com.james.LMS.service.ProductService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
  private final ProductRepository products;
  private final ProductImageRepository images;
  private final UserRepository users;

  @Override
  @Transactional(readOnly = true)
  public ProductDetailResponse getPublished(Long id) {
    var product = products.findPublished(id).orElseThrow(
        () -> new SellerProductException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy thực đơn."));
    var sellerName = product.getSellerId() == null ? "" : users.findById(product.getSellerId())
        .map(u -> u.getUsername()).orElse("");
    return ProductDetailResponse.from(product, images.findForProduct(id), sellerName);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PublicMenuCardResponse> listPublished(
      String query, String category, BigDecimal minPrice, BigDecimal maxPrice,
      int page, int size, String sort) {
    if (page < 0 || size < 1 || size > 48) {
      throw new IllegalArgumentException("Trang hoặc số thực đơn mỗi trang không hợp lệ.");
    }
    if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0
        || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
      throw new IllegalArgumentException("Khoảng giá không hợp lệ.");
    }
    String search = query == null ? "" : query.trim();
    if (search.length() > 100) {
      throw new IllegalArgumentException("Từ khóa tìm kiếm quá dài.");
    }
    String selectedCategory = category == null ? "" : category.trim();
    if (selectedCategory.length() > 100) {
      throw new IllegalArgumentException("Danh mục không hợp lệ.");
    }
    Sort ordering = switch (sort == null ? "newest" : sort) {
      case "price_asc" -> Sort.by(Sort.Order.asc("unitPrice"), Sort.Order.desc("id"));
      case "price_desc" -> Sort.by(Sort.Order.desc("unitPrice"), Sort.Order.desc("id"));
      case "name" -> Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
      default -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
    };

    var result = products.findPublicMenus(
        ProductStatus.PUBLISHED, search, selectedCategory, minPrice, maxPrice,
        PageRequest.of(page, size, ordering));
    if (result.isEmpty()) {
      return result.map(p -> new PublicMenuCardResponse(p.getId(), p.getName(), p.getCategory(),
          p.getUnitPrice().toPlainString(), p.getMinGuests(), p.getMaxGuests(),
          p.getDishItems(), p.getImageUrl(), ""));
    }

    // Batch associations to avoid one database query per menu card.
    Set<Long> sellerIds = result.getContent().stream().map(p -> p.getSellerId())
        .filter(Objects::nonNull).collect(Collectors.toSet());
    Map<Long, String> sellerNames = new HashMap<>();
    if (!sellerIds.isEmpty()) {
      users.findAllById(sellerIds).forEach(u -> sellerNames.put(u.getId(), u.getUsername()));
    }
    List<Long> productIds = result.getContent().stream().map(p -> p.getId()).toList();
    Map<Long, String> coverUrls = new HashMap<>();
    for (ProductImage img : images.findActiveForProducts(productIds)) {
      if (img.isCover()) coverUrls.put(img.getProductId(), img.getImageUrl());
      else coverUrls.putIfAbsent(img.getProductId(), img.getImageUrl());
    }
    return result.map(p -> new PublicMenuCardResponse(
        p.getId(), p.getName(), p.getCategory(), p.getUnitPrice().toPlainString(),
        p.getMinGuests(), p.getMaxGuests(), p.getDishItems(),
        coverUrls.getOrDefault(p.getId(), p.getImageUrl()),
        sellerNames.getOrDefault(p.getSellerId(), "")));
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> publicCategories() {
    return products.findPublicCategories(ProductStatus.PUBLISHED);
  }
}
