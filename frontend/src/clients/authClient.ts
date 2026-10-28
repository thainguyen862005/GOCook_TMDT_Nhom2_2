import { httpRequest, unwrap } from './httpClient';
import type { BaseResponse } from '../types/api';
import type { LoginPayload, RegisterPayload, ResetPasswordPayload, VerifyOtpPayload } from '../types/auth';
import type { UserProfile } from '../types/user';

export const authClient = {
  async googleNonce(): Promise<string> {
    const result = await httpRequest<BaseResponse<{ nonce: string }>>('/api/v2/users/google/nonce');
    return unwrap(result).nonce;
  },
  async googleSignIn(credential: string): Promise<void> {
    await httpRequest('/api/v2/users/google/sign-in', { method: 'POST', body: JSON.stringify({ credential }) });
  },
  async linkGoogle(credential: string): Promise<void> {
    await httpRequest('/api/v2/users/google/link', { method: 'POST', body: JSON.stringify({ credential }) });
  },
  async setInitialPassword(password: string, confirmPassword: string): Promise<void> {
    await httpRequest('/api/v2/users/google/set-password', { method: 'POST', body: JSON.stringify({ password, confirmPassword }) });
  },

  async login(payload: LoginPayload): Promise<void> {
    await httpRequest('/api/v2/users/login', { method: 'POST', body: JSON.stringify(payload) });
  },

  async register(payload: RegisterPayload): Promise<void> {
    await httpRequest('/api/v1/users/sign-up', { method: 'POST', body: JSON.stringify(payload) });
  },

  async verifyEmail(payload: VerifyOtpPayload): Promise<void> {
    await httpRequest('/api/v1/users/verify-email', { method: 'POST', body: JSON.stringify(payload) });
  },

  async requestEmailVerification(email: string): Promise<void> {
    await httpRequest('/api/v1/users/request-email-verification', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  },

  // Backward-compatible alias while older screens are being migrated.
  async resendVerification(email: string): Promise<void> {
    await this.requestEmailVerification(email);
  },

  async forgotPassword(email: string): Promise<void> {
    await httpRequest('/api/v1/users/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  },

  async verifyResetOtp(payload: VerifyOtpPayload): Promise<string> {
    const response = await httpRequest<BaseResponse<{ resetPasswordToken: string }>>('/api/v1/users/verify-otp', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    return unwrap(response).resetPasswordToken;
  },

  async resetPassword(payload: ResetPasswordPayload): Promise<void> {
    await httpRequest('/api/v1/users/reset-password', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  async logout(): Promise<void> {
    await httpRequest('/api/v1/users/logout', { method: 'POST' });
  },

  async profile(): Promise<UserProfile> {
    const response = await httpRequest<BaseResponse<UserProfile>>('/api/v1/users/profile');
    return unwrap(response);
  },
};
