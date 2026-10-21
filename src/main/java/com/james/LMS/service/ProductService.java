package com.james.LMS.service;

import com.james.LMS.response.ProductDetailResponse;
import com.james.LMS.response.PublicMenuCardResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;

public interface ProductService {
  ProductDetailResponse getPublished(Long id);

  Page<PublicMenuCardResponse> listPublished(
      String query, String category, BigDecimal minPrice, BigDecimal maxPrice,
      int page, int size, String sort);

  Page<PublicMenuCardResponse> listPublishedForChef(
      Long chefId, String query, String category, BigDecimal minPrice, BigDecimal maxPrice,
      int page, int size, String sort);

  List<String> publicCategories();
}
