package com.james.LMS.admin.media;

import com.james.LMS.config.SecurityUserDetails;
import com.james.LMS.entity.Product;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/product-gallery")
@PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class AdminProductGalleryController {
  private final AdminProductGalleryService gallery;

  @GetMapping("/{id}")
  public List<AdminProductGalleryService.GalleryImage> images(@PathVariable Long id) { return gallery.getGallery(id); }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestPart("data") AdminGalleryRequest request,
      @RequestPart(value = "files", required = false) List<MultipartFile> files) {
    Product saved = gallery.save(null, request, files, actor.getId());
    return Map.of("product", saved, "images", gallery.getGallery(saved.getId()));
  }

  @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> update(@PathVariable Long id, @AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestPart("data") AdminGalleryRequest request,
      @RequestPart(value = "files", required = false) List<MultipartFile> files) {
    Product saved = gallery.save(id, request, files, actor.getId());
    return Map.of("product", saved, "images", gallery.getGallery(saved.getId()));
  }
}
