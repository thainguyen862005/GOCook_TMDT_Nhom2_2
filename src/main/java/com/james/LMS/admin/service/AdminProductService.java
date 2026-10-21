package com.james.LMS.admin.service;

import com.james.LMS.admin.exception.AdminResourceNotFoundException;
import com.james.LMS.entity.Product;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.admin.util.AdminPaging;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminProductService {

    private static final List<String> DEFAULT_CATEGORIES =
            List.of("Món miền Nam", "Món miền Bắc", "Món miền Trung", "Món chay", "Khác");

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<Product> search(
            String query, String category, String status, int page, int size) {
        return productRepository.search(
                normalize(query),
                normalize(category),
                parseStatus(status),
                AdminPaging.of(page, size));
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return productRepository
                .findById(id)
                .orElseThrow(() -> new AdminResourceNotFoundException("Không tìm thấy sản phẩm."));
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        LinkedHashSet<String> categories = new LinkedHashSet<>(DEFAULT_CATEGORIES);
        categories.addAll(productRepository.findDistinctCategories());
        return List.copyOf(categories);
    }

    public ProductStatus[] statuses() {
        return ProductStatus.values();
    }

    @Transactional
    public Product create(
            String name,
            String category,
            BigDecimal unitPrice,
            Integer minGuests,
            Integer maxGuests,
            String dishItems,
            String description,
            String imageUrl,
            ProductStatus status) {
        validate(
                name,
                category,
                unitPrice,
                minGuests,
                maxGuests,
                imageUrl,
                status);
        if (status == ProductStatus.PUBLISHED) {
            throw new IllegalArgumentException("Thực đơn mới phải gắn Chef; hãy lưu bản nháp trước.");
        }

        return productRepository.save(
                Product.builder()
                        .name(name.trim())
                        .category(category.trim())
                        .unitPrice(unitPrice)
                        .minGuests(minGuests)
                        .maxGuests(maxGuests)
                        .dishItems(cleanOptional(dishItems))
                        .description(cleanOptional(description))
                        .imageUrl(cleanOptional(imageUrl))
                        .status(status)
                        .build());
    }

    @Transactional
    public void update(
            Long id,
            String name,
            String category,
            BigDecimal unitPrice,
            Integer minGuests,
            Integer maxGuests,
            String dishItems,
            String description,
            String imageUrl,
            ProductStatus status) {
        validate(
                name,
                category,
                unitPrice,
                minGuests,
                maxGuests,
                imageUrl,
                status);
        Product product = get(id);
        if (!product.isActive()) {
            throw new IllegalArgumentException("Thực đơn đã bị vô hiệu hóa.");
        }
        if (status == ProductStatus.PUBLISHED && product.getSellerId() == null) {
            throw new IllegalArgumentException("Không thể công khai thực đơn chưa gắn Chef.");
        }
        product.setName(name.trim());
        product.setCategory(category.trim());
        product.setUnitPrice(unitPrice);
        product.setMinGuests(minGuests);
        product.setMaxGuests(maxGuests);
        product.setDishItems(cleanOptional(dishItems));
        product.setDescription(cleanOptional(description));
        product.setImageUrl(cleanOptional(imageUrl));
        product.setStatus(status);
        product.touch();
        productRepository.save(product);
    }

    @Transactional
    public void toggleVisibility(Long id) {
        Product product = get(id);
        if (!product.isActive()) {
            throw new IllegalArgumentException("Thực đơn đã bị vô hiệu hóa.");
        }
        if (product.getStatus() != ProductStatus.PUBLISHED && product.getSellerId() == null) {
            throw new IllegalArgumentException("Thực đơn cần được gắn Chef trước khi công khai.");
        }
        if (product.getStatus() == ProductStatus.PUBLISHED) {
            product.setStatus(ProductStatus.HIDDEN);
        } else {
            if (product.getUnitPrice() == null || product.getUnitPrice().signum() <= 0 ||
                product.getDishItems() == null || product.getDishItems().isBlank() ||
                product.getImageUrl() == null || product.getImageUrl().isBlank()) {
                throw new IllegalArgumentException("Cần giá, danh sách món và ảnh đại diện hợp lệ để công khai.");
            }
            product.setStatus(ProductStatus.PUBLISHED);
        }
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public long countPublished() {
        return productRepository.countByStatus(ProductStatus.PUBLISHED);
    }

    private ProductStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ProductStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Bộ lọc trạng thái thực đơn không hợp lệ.");
        }
    }

    private void validate(
            String name,
            String category,
            BigDecimal unitPrice,
            Integer minGuests,
            Integer maxGuests,
            String imageUrl,
            ProductStatus status) {

        requireText(name, "Tên thực đơn không được để trống.");
        requireText(category, "Danh mục không được để trống.");
        if (name.trim().length() > 255 || category.trim().length() > 100) {
            throw new IllegalArgumentException("Tên hoặc danh mục vượt quá độ dài cho phép.");
        }

        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Giá mỗi người phải lớn hơn hoặc bằng 0.");
        }

        if (unitPrice != null && (unitPrice.compareTo(new BigDecimal("999999999999.99")) > 0
                || unitPrice.scale() > 2)) {
            throw new IllegalArgumentException("Giá vượt giới hạn hoặc có quá 2 chữ số thập phân.");
        }
        if (minGuests == null || maxGuests == null || minGuests < 1 || maxGuests < 1) {
            throw new IllegalArgumentException("Số khách phải lớn hơn 0.");
        }

        if (minGuests > maxGuests) {
            throw new IllegalArgumentException("Số khách tối thiểu không thể lớn hơn số khách tối đa.");
        }

        if (status == null) {
            throw new IllegalArgumentException("Trạng thái sản phẩm không hợp lệ.");
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            String trimmed = imageUrl.trim();
            // Existing local assets are valid URLs for the built-in sample menus.
            if (trimmed.length() > 1000 || !(trimmed.startsWith("/assets/")
                    || trimmed.matches("(?i)^https?://[^\\s/]+(?:/.*)?$"))) {
                throw new IllegalArgumentException("URL ảnh phải là HTTPS/HTTP hoặc /assets/ hợp lệ.");
            }
        }
        if (status == ProductStatus.PUBLISHED && unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("Thực đơn hiển thị phải có giá lớn hơn 0.");
        }
        if (status == ProductStatus.PUBLISHED && (imageUrl == null || imageUrl.isBlank())) {
            throw new IllegalArgumentException("Thực đơn đang hiển thị phải có ảnh đại diện.");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
