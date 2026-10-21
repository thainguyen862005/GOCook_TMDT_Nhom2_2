package com.james.LMS.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.james.LMS.enums.ErrorCode;
import com.james.LMS.enums.FileType;
import com.james.LMS.exception.SellerProductException;
import com.james.LMS.exception.UploadFileException;
import com.james.LMS.media.MediaStorageUnavailableException;
import com.james.LMS.service.CloudinaryService;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {
  private final Cloudinary cloudinary;

  @Override
  public MenuImage uploadMenuImage(byte[] image) {
    String publicId = "gocook/menus/" + UUID.randomUUID();
    try {
      var result =
          cloudinary
              .uploader()
              .upload(
                  image,
                  ObjectUtils.asMap(
                      "public_id", publicId, "resource_type", "image", "overwrite", false));
      String url = (String) result.get("secure_url");
      URI uri = URI.create(url);
      if (!"https".equals(uri.getScheme())
          || !"res.cloudinary.com".equals(uri.getHost())
          || url.length() > 1000) {
        throw new IOException("Invalid Cloudinary image response");
      }
      return new MenuImage(url, publicId);
    } catch (Exception error) {
      try {
        deleteMenuImage(publicId);
      } catch (Exception cleanup) {
        log.warn("Could not clean up an unsuccessful menu upload: {}", publicId);
      }
      throw new SellerProductException(
          HttpStatus.BAD_GATEWAY,
          "MENU_UPLOAD_FAILED",
          "Không tải được ảnh lên Cloudinary. Thực đơn chưa được lưu, vui lòng thử lại.");
    }
  }

  @Override
  public MenuImage uploadSiteImage(byte[] image) {
    String publicId = "gocook/site/" + UUID.randomUUID();
    try {
      var result = cloudinary.uploader().upload(image, ObjectUtils.asMap(
          "public_id", publicId, "resource_type", "image", "overwrite", false));
      String url = (String) result.get("secure_url");
      URI uri = URI.create(url);
      if (!"https".equals(uri.getScheme()) || !"res.cloudinary.com".equals(uri.getHost()) || url.length() > 1000)
        throw new IOException("Invalid Cloudinary image URL");
      return new MenuImage(url, publicId);
    } catch (Exception error) {
      try { deleteSiteImage(publicId); } catch (Exception ignored) { log.warn("Site media cleanup failed: {}", publicId); }
      throw new MediaStorageUnavailableException("Không tải được ảnh lên Cloudinary. Vui lòng kiểm tra cấu hình và thử lại.", error);
    }
  }

  @Override
  public void deleteSiteImage(String publicId) {
    if (publicId == null || !publicId.startsWith("gocook/site/")) return;
    try {
      var response = cloudinary.uploader().destroy(publicId,
          ObjectUtils.asMap("resource_type", "image", "invalidate", true));
      if (!"ok".equals(response.get("result")) && !"not found".equals(response.get("result")))
        throw new IOException("Cloudinary deletion failed");
    } catch (IOException ex) { throw new IllegalStateException("Cloudinary deletion failed", ex); }
  }

  @Override
  public void deleteMenuImage(String publicId) {
    if (publicId == null || !publicId.startsWith("gocook/menus/")) return;
    try {
      var result =
          cloudinary
              .uploader()
              .destroy(publicId, ObjectUtils.asMap("resource_type", "image", "invalidate", true));
      if (!"ok".equals(result.get("result")) && !"not found".equals(result.get("result"))) {
        throw new IOException("Cloudinary deletion failed");
      }
    } catch (IOException error) {
      throw new IllegalStateException("Could not delete menu image", error);
    }
  }

  @Override
  public String uploadFile(byte[] image, FileType resourceType) {

    var params = ObjectUtils.asMap("folder", "users", "resource_type", resourceType.getType());
    try {
      var uploadResult = cloudinary.uploader().upload(image, params);
      return uploadResult.get("secure_url").toString();
    } catch (IOException e) {
      throw new UploadFileException(ErrorCode.FILE_ERROR_UPLOAD);
    }
  }
}
