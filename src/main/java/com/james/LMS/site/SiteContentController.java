package com.james.LMS.site;

import com.james.LMS.config.SecurityUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class SiteContentController {
  private final SiteContentService service;

  @GetMapping("/api/public/site-settings")
  public SiteSettings publicSettings() { return service.read(); }

  @GetMapping("/api/admin/site-settings")
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  public SiteSettings adminSettings() { return service.read(); }

  @PutMapping(value = "/api/admin/site-settings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
  public SiteSettings update(@AuthenticationPrincipal SecurityUserDetails actor,
      @Valid @RequestPart("settings") SiteSettingsRequest settings,
      @RequestPart(value = "hero", required = false) MultipartFile hero,
      @RequestPart(value = "promo", required = false) MultipartFile promo,
      @RequestPart(value = "logo", required = false) MultipartFile logo) {
    return service.save(settings, hero, promo, logo, actor.getId());
  }
}
