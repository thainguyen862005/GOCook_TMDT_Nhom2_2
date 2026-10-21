export interface SiteSettings {
  version: number;
  siteName: string;
  tagline: string;
  description: string;
  heroTitle: string;
  heroSubtitle: string;
  heroImageUrl: string;
  heroImageAlt: string;
  promoImageUrl: string;
  logoUrl: string;
  contactEmail: string;
  contactPhone: string;
  facebookUrl: string;
  instagramUrl: string;
  footerCopyright: string;
}
export const defaultSite: SiteSettings = {
  version: 0, siteName: "GO Cook", tagline: "Đầu bếp tại gia",
  description: "Kết nối đầu bếp và gia đình Việt. Bữa cơm tươi ngon, được chăm chút ngay trong gian bếp của bạn.",
  heroTitle: "Đầu bếp bạn chọn. Bữa ăn bạn muốn. Ngay tại nhà.",
  heroSubtitle: "Một bữa cơm ngon, một buổi tối thảnh thơi. GO Cook kết nối bạn với đầu bếp tận tâm.",
  heroImageUrl: "/assets/hero.png", heroImageAlt: "Đầu bếp nấu ăn tại gia đình",
  promoImageUrl: "/assets/meal.png", logoUrl: "", contactEmail: "", contactPhone: "",
  facebookUrl: "", instagramUrl: "", footerCopyright: "© 2026 GO Cook",
};
