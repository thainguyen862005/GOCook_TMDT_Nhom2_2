import { httpRequest } from "./httpClient";
import type { SiteSettings } from "../types/siteSettings";
export const siteSettingsClient = {
  public: (signal?: AbortSignal) => httpRequest<SiteSettings>("/api/public/site-settings", { signal }),
  admin: () => httpRequest<SiteSettings>("/api/admin/site-settings"),
  save(settings: SiteSettings, files: { hero?: File; promo?: File; logo?: File },
      clear: { hero?: boolean; promo?: boolean; logo?: boolean }) {
    const body = new FormData();
    body.append("settings", new Blob([JSON.stringify({
      ...settings,
      clearHero: Boolean(clear.hero), clearPromo: Boolean(clear.promo), clearLogo: Boolean(clear.logo),
    })], { type: "application/json" }));
    if (files.hero) body.append("hero", files.hero);
    if (files.promo) body.append("promo", files.promo);
    if (files.logo) body.append("logo", files.logo);
    return httpRequest<SiteSettings>("/api/admin/site-settings", { method: "PUT", body });
  },
};
