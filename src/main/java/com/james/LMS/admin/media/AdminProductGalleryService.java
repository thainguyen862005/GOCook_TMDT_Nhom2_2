package com.james.LMS.admin.media;

import com.james.LMS.admin.dto.request.ProductRequest;
import com.james.LMS.admin.service.AdminProductService;
import com.james.LMS.admin.operations.AdminAuditService;
import com.james.LMS.entity.Product;
import com.james.LMS.entity.ProductImage;
import com.james.LMS.enums.ProductStatus;
import com.james.LMS.media.ImageFileValidator;
import com.james.LMS.repository.ProductImageRepository;
import com.james.LMS.repository.ProductRepository;
import com.james.LMS.service.CloudinaryService;
import com.james.LMS.service.impl.ProductImageCleanupService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminProductGalleryService {
  private final AdminProductService admin;
  private final ProductRepository products;
  private final ProductImageRepository images;
  private final CloudinaryService cloudinary;
  private final ProductImageCleanupService cleanup;
  private final AdminAuditService audit;

  @Transactional(readOnly = true)
  public List<GalleryImage> getGallery(Long productId) {
    Product product = admin.get(productId);
    List<GalleryImage> result = images.findForProduct(productId).stream()
        .filter(ProductImage::isActive)
        .map(i -> new GalleryImage("existing:" + i.getId(), i.getImageUrl(), i.getAltText(), i.isCover(), i.getDisplayOrder()))
        .toList();
    if (result.isEmpty() && product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
      return List.of(new GalleryImage("legacy:cover", product.getImageUrl(), product.getName(), true, 0));
    }
    return result;
  }
  public record GalleryImage(String key, String url, String alt, boolean cover, int order) {}

  /** Single transaction for product fields and gallery; uploaded files are compensated on DB rollback. */
  @Transactional
  public Product save(Long id, AdminGalleryRequest request, List<MultipartFile> fileParts, long actorId) {
    if (request == null || request.product() == null || request.imageKeys() == null)
      throw new IllegalArgumentException("Thông tin thực đơn không hợp lệ.");
    List<MultipartFile> files = fileParts == null ? List.of() : fileParts;
    List<String> keys = request.imageKeys();
    if (request.imageAlts() == null || request.imageAlts().size() != keys.size())
      throw new IllegalArgumentException("Tên thay thế ảnh không khớp danh sách ảnh.");
    if (keys.size() > 10 || new HashSet<>(keys).size() != keys.size() || files.size() > 10)
      throw new IllegalArgumentException("Tối đa 10 ảnh, không được chọn ảnh trùng.");
    for (int i = 0; i < files.size(); i++)
      if (!keys.contains("new:" + i)) throw new IllegalArgumentException("Danh sách ảnh tải lên không khớp.");
    if ((!keys.isEmpty() && !keys.contains(request.coverKey())) || (keys.isEmpty() && request.coverKey() != null && !request.coverKey().isEmpty()))
      throw new IllegalArgumentException("Vui lòng chọn đúng một ảnh bìa.");
    ProductRequest input = request.product();
    if (input.status() == ProductStatus.PUBLISHED && keys.isEmpty())
      throw new IllegalArgumentException("Cần ít nhất một ảnh để công khai thực đơn.");
    List<byte[]> validated = files.stream().map(ImageFileValidator::validate).toList();
    Product product;
    if (id == null) {
      if (input.status() == ProductStatus.PUBLISHED)
        throw new IllegalArgumentException("Thực đơn mới phải ở trạng thái bản nháp cho đến khi Chef nhận quản lý.");
      product = admin.create(input.name(), input.category(), input.unitPrice(), input.minGuests(), input.maxGuests(),
          input.dishItems(), input.description(), null, ProductStatus.DRAFT);
    } else {
      product = admin.get(id);
      if (request.version() == null || !Objects.equals(request.version(), product.getVersion()))
        throw new IllegalStateException("Thực đơn đã được thay đổi ở phiên khác. Hãy tải lại trước khi lưu.");
    }
    List<ProductImage> old = images.findForProduct(product.getId());
    Map<String, ProductImage> available = new HashMap<>();
    for (ProductImage image : old) if (image.isActive()) available.put("existing:" + image.getId(), image);
    if (available.isEmpty() && product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
      available.put("legacy:cover", ProductImage.builder().productId(product.getId())
          .imageUrl(product.getImageUrl()).altText(product.getName()).build());
    }
    for (String key : keys) {
      if (!available.containsKey(key) && !validNewKey(key, validated.size()))
        throw new IllegalArgumentException("Ảnh không thuộc thực đơn hoặc ảnh không hợp lệ.");
    }
    List<String> newlyUploaded = new ArrayList<>();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int status) {
        if (status != STATUS_COMMITTED) for (String publicId : newlyUploaded) {
          try { cloudinary.deleteMenuImage(publicId); }
          catch (Exception ex) { log.warn("Pending admin menu upload rollback cleanup {}", publicId); }
        }
      }
      @Override public void afterCommit() {
        for (ProductImage image : old) if (!image.isActive() && image.getCloudinaryPublicId() != null) {
          try { cleanup.cleanup(image.getId()); }
          catch (Exception ex) { log.warn("Admin image cleanup pending {}", image.getId()); }
        }
      }
    });
    // Clear previous cover before reassigning; DB has a unique partial index for active cover.
    for (ProductImage image : old) {
      image.setCover(false);
      if (!keys.contains("existing:" + image.getId())) image.deactivate();
    }
    images.flush();
    for (int i = 0; i < validated.size(); i++) {
      CloudinaryService.MenuImage uploaded = cloudinary.uploadMenuImage(validated.get(i));
      newlyUploaded.add(uploaded.publicId());
      String alt = files.get(i).getOriginalFilename() == null ? input.name() : files.get(i).getOriginalFilename();
      available.put("new:" + i, ProductImage.builder().productId(product.getId())
          .imageUrl(uploaded.url()).cloudinaryPublicId(uploaded.publicId())
          .altText(alt.substring(0, Math.min(alt.length(), 255))).build());
    }
    List<ProductImage> ordered = new ArrayList<>();
    for (int i = 0; i < keys.size(); i++) {
      ProductImage image = available.get(keys.get(i));
      image.setCover(keys.get(i).equals(request.coverKey()));
      image.setDisplayOrder(i);
      String alt = request.imageAlts().get(i);
      if (alt == null || alt.isBlank()) alt = input.name();
      if (alt.length() > 255) throw new IllegalArgumentException("Mô tả ảnh không được quá 255 ký tự.");
      image.setAltText(alt.trim());
      ordered.add(image);
    }
    images.saveAllAndFlush(ordered);
    String coverUrl = ordered.stream().filter(ProductImage::isCover).map(ProductImage::getImageUrl).findFirst().orElse(null);
    admin.update(product.getId(), input.name(), input.category(), input.unitPrice(), input.minGuests(),
        input.maxGuests(), input.dishItems(), input.description(), coverUrl, input.status());
    audit.record(actorId, id == null ? "PRODUCT_CREATE" : "PRODUCT_UPDATE", "PRODUCT",
        product.getId(), "Cập nhật thực đơn và album ảnh");
    return admin.get(product.getId());
  }

  private boolean validNewKey(String key, int count) {
    if (key == null) return false;
    for (int i = 0; i < count; i++) if (key.equals("new:" + i)) return true;
    return false;
  }
}
