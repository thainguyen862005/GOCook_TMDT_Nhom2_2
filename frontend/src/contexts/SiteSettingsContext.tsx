import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import type { SiteSettings } from "../types/siteSettings";
import { defaultSite } from "../types/siteSettings";
import { siteSettingsClient } from "../clients/siteSettingsClient";

const Context = createContext<SiteSettings>(defaultSite);
export function SiteSettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<SiteSettings>(defaultSite);
  useEffect(() => {
    const controller = new AbortController();
    siteSettingsClient.public(controller.signal)
      .then(setSettings)
      .catch(() => { /* Keep local default visuals when CMS is unreachable. */ });
    return () => controller.abort();
  }, []);
  return <Context.Provider value={settings}>{children}</Context.Provider>;
}
export const useSiteSettings = () => useContext(Context);
