package com.james.LMS.service.impl;

import com.james.LMS.entity.Product;
import com.james.LMS.entity.ProductImage;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.exception.SellerProductException;
import com.james.LMS.repository.ProductImageRepository;
import com.james.LMS.media.ImageFileValidator;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.repository.UserRepository;
import com.james.LMS.request.SellerProductRequest;
import com.james.LMS.request.SellerProductStatusRequest;
import com.james.LMS.response.SellerProductResponse;
import com.james.LMS.service.CloudinaryService;
import com.james.LMS.service.SellerProductService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerProductServiceImpl implements SellerProductService {
  public static final List<String> CATEGORIES =
      List.of("Món miền Nam", "Món miền Bắc", "Món miền Trung", "Món chay", "Khác");
  private final ProductRepository products;
  private final ProductImageRepository images;
  private final UserRepository users;
  private final CloudinaryService cloudinary;
  private final ProductImageCleanupService cleanup;

  @Override
  @Transactional(readOnly = true)
  public List<String> categories(Long sellerId) {
    requireActive(sellerId);
    var categories = new LinkedHashSet<>(CATEGORIES);
    categories.addAll(products.findOwnedCategories(sellerId));
    return List.copyOf(categories);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<SellerProductResponse> search(
      Long sellerId, String query, String category, ProductStatus status, int page, int size) {
    requireActive(sellerId);
    if (page < 0 || size < 1 || size > 100) throw invalid("Phân trang không hợp lệ.");
    Page<Product> result =
        products.searchOwned(
            sellerId,
            clean(query),
            clean(category),
            status,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
    Map<Long, List<ProductImage>> grouped = new HashMap<>();
    if (!result.isEmpty()) {
      for (ProductImage image :
          images.findActiveForProducts(result.getContent().stream().map(Product::getId).toList())) {
        grouped.computeIfAbsent(image.getProductId(), key -> new ArrayList<>()).add(image);
      }
    }
    return result.map(
        product ->
            SellerProductResponse.from(product, grouped.getOrDefault(product.getId(), List.of())));
  }

  @Override
  @Transactional(readOnly = true)
  public SellerProductResponse get(Long sellerId, Long id) {
    requireActive(sellerId);
    Product product = products.findOwned(id, sellerId).orElseThrow(this::notFound);
    return SellerProductResponse.from(product, images.findForProduct(id));
  }

  @Override
  @Transactional
  public SellerProductResponse create(
      Long sellerId, SellerProductRequest request, List<MultipartFile> files) {
    requireActive(sellerId);
    Product product = Product.builder().sellerId(sellerId).build();
    return save(product, request, files == null ? List.of() : files);
  }

  @Override
  @Transactional
  public SellerProductResponse update(
      Long sellerId, Long id, SellerProductRequest request, List<MultipartFile> files) {
    Product product = ownedLocked(sellerId, id, request.version());
    return save(product, request, files == null ? List.of() : files);
  }

  @Override
  @Transactional
  public SellerProductResponse changeStatus(
      Long sellerId, Long id, SellerProductStatusRequest request) {
    Product product = ownedLocked(sellerId, id, request.version());
    List<ProductImage> current = images.findForProduct(id);
    if (request.status() == ProductStatus.PUBLISHED
        && current.stream().noneMatch(image -> image.isActive() && image.isCover())) {
      throw invalid("Thực đơn hiển thị phải có ảnh bìa. Hãy mở chỉnh sửa để thêm ảnh.");
    }
    product.setStatus(request.status());
    product.touch();
    products.flush();
    return SellerProductResponse.from(product, current);
  }

  private Product ownedLocked(Long sellerId, Long id, Long version) {
    requireActive(sellerId);
    Product product = products.lockOwned(id, sellerId).orElseThrow(this::notFound);
    if (version == null || !Objects.equals(product.getVersion(), version)) {
      throw new SellerProductException(
          HttpStatus.CONFLICT,
          "MENU_VERSION_CONFLICT",
          "Thực đơn đã thay đổi ở một phiên khác. Tải lại dữ liệu trước khi sửa tiếp.");
    }
    return product;
  }

  private SellerProductResponse save(
      Product product, SellerProductRequest request, List<MultipartFile> files) {
    if (request.minGuests() > request.maxGuests())
      throw invalid("Danh mục hoặc số khách không hợp lệ.");
    List<ProductImage> current =
        product.getId() == null ? new ArrayList<>() : images.findForProduct(product.getId());
    Map<String, ProductImage> available = new HashMap<>();
    current.stream()
        .filter(ProductImage::isActive)
        .forEach(image -> available.put("existing:" + image.getId(), image));
    List<String> keys = request.imageKeys();
    if (keys.size() > 10 || new HashSet<>(keys).size() != keys.size() || files.size() > 10)
      throw invalid("Tối đa 10 ảnh, không lặp ảnh.");
    for (int i = 0; i < files.size(); i++) {
      if (!keys.contains("new:" + i)) throw invalid("Danh sách ảnh tải lên không khớp.");
    }
    for (String key : keys) {
      if (!available.containsKey(key) && !isNewKey(key, files.size()))
        throw invalid("Ảnh không thuộc thực đơn hoặc không hợp lệ.");
    }
    if (!keys.isEmpty() && !keys.contains(request.coverKey()))
      throw invalid("Vui lòng chọn đúng một ảnh bìa.");
    if (request.status() == ProductStatus.PUBLISHED && keys.isEmpty())
      throw invalid("Thực đơn hiển thị phải có ít nhất một ảnh.");
    List<byte[]> content = files.stream().map(file -> {
      try { return ImageFileValidator.validate(file); }
      catch (IllegalArgumentException error) { throw invalid(error.getMessage()); }
    }).toList();
    List<String> uploaded = new ArrayList<>();
    Consumer<String> deleteUploaded = this::tryDelete;
    ProductImageCleanupService cleanupService = this.cleanup;
    // Compensation: keep old images until DB commit; remove new uploads if the transaction fails.
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED)
              uploaded.forEach(deleteUploaded); // Rollback: delete new uploads if transaction fails.
          }

          @Override
          public void afterCommit() {
            for (ProductImage image : current) {
              if (!image.isActive() && image.getCloudinaryPublicId() != null) {
                try {
                  cleanupService.cleanup(image.getId());
                } catch (Exception error) {
                  log.warn("Menu image cleanup pending for image {}", image.getId());
                }
              }
            }
          }
        });
    product.setName(request.name().trim());
    product.setCategory(request.category().trim());
    product.setUnitPrice(request.unitPrice());
    product.setMinGuests(request.minGuests());
    product.setMaxGuests(request.maxGuests());
    product.setDishItems(request.dishItems().trim());
    product.setDescription(clean(request.description()));
    product.setStatus(request.status());
    product.touch();
    product = products.saveAndFlush(product);
    // Clear the previous cover first to satisfy the partial unique index when swapping covers.
    current.forEach(
        image -> {
          image.setCover(false);
          if (!keys.contains("existing:" + image.getId())) image.deactivate();
        });
    images.flush();
    for (int i = 0; i < content.size(); i++) {
      CloudinaryService.MenuImage upload = cloudinary.uploadMenuImage(content.get(i));
      uploaded.add(upload.publicId());
      String filename = clean(files.get(i).getOriginalFilename());
      available.put(
          "new:" + i,
          ProductImage.builder()
              .productId(product.getId())
              .imageUrl(upload.url())
              .cloudinaryPublicId(upload.publicId())
              .altText(filename.substring(0, Math.min(filename.length(), 255)))
              .build());
    }
    List<ProductImage> ordered = new ArrayList<>();
    for (int i = 0; i < keys.size(); i++) {
      ProductImage image = available.get(keys.get(i));
      image.setDisplayOrder(i);
      image.setCover(keys.get(i).equals(request.coverKey()));
      ordered.add(image);
    }
    ordered = images.saveAllAndFlush(ordered);
    product.setImageUrl(
        ordered.stream()
            .filter(ProductImage::isCover)
            .findFirst()
            .map(ProductImage::getImageUrl)
            .orElse(null));
    products.flush();
    return SellerProductResponse.from(product, ordered);
  }

  private boolean isNewKey(String key, int count) {
    for (int i = 0; i < count; i++) if (("new:" + i).equals(key)) return true;
    return false;
  }

  private void requireActive(Long sellerId) {
    if (sellerId == null || users.findById(sellerId).filter(user -> user.isActive()).isEmpty()) {
      throw new SellerProductException(
          HttpStatus.FORBIDDEN, "MENU_ACCOUNT_INACTIVE", "Tài khoản không thể quản lý thực đơn.");
    }
  }

  private void tryDelete(String publicId) {
    try {
      cloudinary.deleteMenuImage(publicId);
    } catch (Exception error) {
      log.warn("Rollback cleanup could not delete new menu upload: {}", publicId);
    }
  }

  private SellerProductException invalid(String message) {
    return new SellerProductException(HttpStatus.BAD_REQUEST, "MENU_VALIDATION", message);
  }

  private SellerProductException notFound() {
    return new SellerProductException(
        HttpStatus.NOT_FOUND, "MENU_NOT_FOUND", "Không tìm thấy thực đơn của bạn.");
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
