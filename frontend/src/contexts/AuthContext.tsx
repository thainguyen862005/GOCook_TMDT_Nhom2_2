import { createContext, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { authClient } from '../clients/authClient';
import { ApiError } from '../clients/httpClient';
import { ROLES } from '../constants/roles';
import type { UserProfile } from '../types/user';

export interface AuthContextValue {
  profile: UserProfile | null;
  loading: boolean;
  roles: string[];
  isAuthenticated: boolean;
  isAdmin: boolean;
  isSeller: boolean;
  emailVerified: boolean;
  login: (email: string, password: string) => Promise<UserProfile | null>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<UserProfile | null>;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  // Prevent an earlier in-flight profile lookup from restoring a stale UI
  // after logout or overwriting a more recent authentication result.
  const authRequestEpoch = useRef(0);

  const refreshProfile = useCallback(async () => {
    const epoch = ++authRequestEpoch.current;
    try {
      const next = await authClient.profile();
      if (epoch !== authRequestEpoch.current) return null;
      setProfile(next);
      return next;
    } catch (error) {
      if (epoch !== authRequestEpoch.current) return null;
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        setProfile(null);
        return null;
      }
      setProfile(null);
      return null;
    } finally {
      if (epoch === authRequestEpoch.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshProfile();
  }, [refreshProfile]);

  const login = useCallback(async (email: string, password: string) => {
    await authClient.login({ email, password });
    return refreshProfile();
  }, [refreshProfile]);

  const logout = useCallback(async () => {
    // The browser still holds a valid HttpOnly cookie when a network request
    // fails. Only mark the UI as signed out after the server confirms logout.
    await authClient.logout();
    authRequestEpoch.current += 1;
    setProfile(null);
    setLoading(false);
  }, []);

  const roles = profile?.roles ?? [];
  const value = useMemo<AuthContextValue>(() => ({
    profile,
    loading,
    roles,
    isAuthenticated: Boolean(profile),
    isAdmin: roles.includes(ROLES.ADMIN),
    isSeller: roles.includes(ROLES.SELLER),
    emailVerified: Boolean(profile?.emailVerified),
    login,
    logout,
    refreshProfile,
  }), [profile, loading, roles, login, logout, refreshProfile]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
