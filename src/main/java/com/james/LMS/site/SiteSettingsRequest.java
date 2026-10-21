package com.james.LMS.site;

import jakarta.validation.constraints.NotNull;

public record SiteSettingsRequest(@NotNull Long version, String siteName, String tagline,
    String description, String heroTitle, String heroSubtitle, String heroImageAlt,
    String contactEmail, String contactPhone, String facebookUrl, String instagramUrl,
    String footerCopyright, Boolean clearHero, Boolean clearPromo, Boolean clearLogo) {}
