package com.james.LMS.site;

import com.james.LMS.media.ImageFileValidator;
import com.james.LMS.admin.operations.AdminAuditService;
import com.james.LMS.service.CloudinaryService;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteContentService {
  private final JdbcTemplate jdbc;
  private final CloudinaryService cloudinary;
  private final AdminAuditService audit;

  @Transactional(readOnly = true)
  public SiteSettings read() {
    try {
      return jdbc.queryForObject("""
          SELECT version, site_name, tagline, description, hero_title, hero_subtitle,
             hero_image_url, hero_image_alt, promo_image_url, logo_url,
             contact_email, contact_phone, facebook_url, instagram_url, footer_copyright
          FROM site_content WHERE id = 1
          """, (rs, row) -> new SiteSettings(
              rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
              rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
              rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
              rs.getString(13), rs.getString(14), rs.getString(15)));
    } catch (EmptyResultDataAccessException ignored) { return SiteSettings.defaults(); }
  }

  @Transactional
  public SiteSettings save(SiteSettingsRequest req, MultipartFile hero, MultipartFile promo,
      MultipartFile logo, long actorId) {
    if (req == null || req.version() == null || req.version() < 0)
      throw new IllegalArgumentException("Phiên bản thông tin website không hợp lệ.");
    // Validate all before any upload or database mutation.
    String name = required(req.siteName(), 100, "Tên website");
    String tagline = required(req.tagline(), 150, "Khẩu hiệu");
    String desc = required(req.description(), 1000, "Mô tả website");
    String title = required(req.heroTitle(), 200, "Tiêu đề banner");
    String subtitle = required(req.heroSubtitle(), 1000, "Nội dung banner");
    String alt = required(req.heroImageAlt(), 200, "Mô tả ảnh banner");
    String email = optional(req.contactEmail(), 254, "Email");
    if (!email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
      throw new IllegalArgumentException("Email liên hệ không hợp lệ.");
    String phone = optional(req.contactPhone(), 30, "Số điện thoại");
    String fb = safeExternalUrl(req.facebookUrl());
    String ig = safeExternalUrl(req.instagramUrl());
    String footer = required(req.footerCopyright(), 160, "Bản quyền");
    byte[] heroBytes = bytes(hero), promoBytes = bytes(promo), logoBytes = bytes(logo);
    if (Boolean.TRUE.equals(req.clearHero()) && heroBytes != null ||
        Boolean.TRUE.equals(req.clearPromo()) && promoBytes != null ||
        Boolean.TRUE.equals(req.clearLogo()) && logoBytes != null)
      throw new IllegalArgumentException("Không thể đồng thời xóa và tải ảnh thay thế.");
    SiteSettings current = read();
    if (req.version().longValue() != current.version())
      throw new IllegalStateException("Thông tin website đã thay đổi. Hãy tải lại trước khi lưu.");
    List<String> uploads = new ArrayList<>();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int status) {
        if (status != STATUS_COMMITTED) for (String id : uploads) {
          try { cloudinary.deleteSiteImage(id); } catch (Exception ex) { log.warn("Pending media rollback cleanup: {}", id); }
        }
      }
    });
    String heroUrl = media(heroBytes, Boolean.TRUE.equals(req.clearHero()), current.heroImageUrl(), uploads, "/assets/hero.png");
    String promoUrl = media(promoBytes, Boolean.TRUE.equals(req.clearPromo()), current.promoImageUrl(), uploads, "");
    String logoUrl = media(logoBytes, Boolean.TRUE.equals(req.clearLogo()), current.logoUrl(), uploads, "");
    int updated = jdbc.update("""
        UPDATE site_content SET version = version + 1,
          site_name=?, tagline=?, description=?, hero_title=?, hero_subtitle=?,
          hero_image_url=?, hero_image_alt=?, promo_image_url=?, logo_url=?,
          contact_email=?, contact_phone=?, facebook_url=?, instagram_url=?, footer_copyright=?,
          updated_by=?, updated_at=CURRENT_TIMESTAMP
        WHERE id=1 AND version=?
        """, name, tagline, desc, title, subtitle, heroUrl, alt, promoUrl, logoUrl,
        email, phone, fb, ig, footer, actorId, req.version());
    if (updated != 1) throw new IllegalStateException("Thông tin website đã thay đổi. Hãy tải lại trước khi lưu.");
    audit.record(actorId, "SITE_SETTINGS_UPDATE", "SITE_CONTENT", 1L,
        "Cập nhật thông tin website và hình ảnh banner");
    // Deliberately retain the previous remote assets. Other references may still use them.
    return read();
  }

  private String media(byte[] data, boolean clear, String old, List<String> uploads, String fallback) {
    if (data == null) return clear ? fallback : old;
    CloudinaryService.MenuImage saved = cloudinary.uploadSiteImage(data);
    uploads.add(saved.publicId());
    return saved.url();
  }
  private byte[] bytes(MultipartFile file) { return file == null ? null : ImageFileValidator.validate(file); }
  private String required(String value, int limit, String field) {
    String s = optional(value, limit, field);
    if (s.isEmpty()) throw new IllegalArgumentException(field + " là bắt buộc.");
    return s;
  }
  private String optional(String value, int limit, String field) {
    String s = value == null ? "" : value.trim();
    if (s.length() > limit) throw new IllegalArgumentException(field + " quá dài.");
    return s;
  }
  private String safeExternalUrl(String value) {
    String s = optional(value, 500, "Liên kết mạng xã hội");
    if (!s.isEmpty()) {
      URI uri;
      try { uri = URI.create(s); } catch (Exception e) { throw new IllegalArgumentException("Liên kết phải là URL HTTPS hợp lệ."); }
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
        throw new IllegalArgumentException("Liên kết phải bắt đầu bằng https:// và có tên miền.");
    }
    return s;
  }
}
