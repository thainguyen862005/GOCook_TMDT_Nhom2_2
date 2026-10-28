import { httpRequest, unwrap } from './httpClient';
import type { BaseResponse } from '../types/api';

export const userClient = {
  async uploadAvatar(file: File): Promise<string> {
    const body = new FormData();
    body.append('image', file);
    const response = await httpRequest<BaseResponse<string>>('/api/v1/users/avatar', { method: 'POST', body });
    return unwrap(response);
  },
  async changePassword(currentPassword: string, newPassword: string, confirmPassword: string): Promise<void> {
    await httpRequest('/api/v1/users/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword, confirmPassword }),
    });
  },
  async updateProfile(username: string, phone: string, defaultAddress: string): Promise<void> {
    await httpRequest('/api/v1/users/profile', {
      method: 'PUT',
      body: JSON.stringify({ username, phone, defaultAddress }),
    });
  },
};
