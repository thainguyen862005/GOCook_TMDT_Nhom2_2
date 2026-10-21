package com.james.LMS.site;

/** Public-facing CMS fields. Avoid sending credentials, email lists or private user data. */
public record SiteSettings(
    long version, String siteName, String tagline, String description,
    String heroTitle, String heroSubtitle, String heroImageUrl, String heroImageAlt,
    String promoImageUrl, String logoUrl, String contactEmail, String contactPhone,
    String facebookUrl, String instagramUrl, String footerCopyright) {
  public static SiteSettings defaults() {
    return new SiteSettings(0, "GO Cook", "Đầu bếp tại gia",
        "Kết nối đầu bếp và gia đình Việt. Bữa cơm tươi ngon, được chăm chút ngay trong gian bếp của bạn.",
        "Đầu bếp bạn chọn. Bữa ăn bạn muốn. Ngay tại nhà.",
        "Một bữa cơm ngon, một buổi tối thảnh thơi. GO Cook kết nối bạn với đầu bếp tận tâm.",
        "/assets/hero.png", "Đầu bếp nấu ăn tại gia đình", "/assets/meal.png", "",
        "", "", "", "", "© 2026 GO Cook");
  }
}
