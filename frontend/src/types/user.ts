export interface UserProfile {
  id: number;
  username: string;
  email: string;
  emailVerified: boolean;
  passwordLoginEnabled?: boolean;
  googleLinked?: boolean;
  avatarUrl?: string | null;
  phone?: string | null;
  defaultAddress?: string | null;
  createdAt?: string | number | null;
  roles: string[];
}
