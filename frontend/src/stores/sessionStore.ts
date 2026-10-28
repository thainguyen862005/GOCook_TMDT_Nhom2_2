// Reset tokens are kept in memory only, not in localStorage/sessionStorage.
// Reloading the page requires requesting and verifying a new OTP.
const tokens = new Map<string, string>();
const key = (email: string) => email.trim().toLowerCase();

export const sessionStore = {
  getResetToken(email: string): string | null { return tokens.get(key(email)) ?? null; },
  setResetToken(email: string, token: string): void { tokens.set(key(email), token); },
  clearResetToken(email: string): void { tokens.delete(key(email)); },
};
