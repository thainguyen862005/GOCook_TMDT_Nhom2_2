import { httpRequest } from './httpClient';
import type { NotificationPage } from '../types/notification';

export const notificationClient = {
  page: (page = 0, size = 15, unreadOnly = false) =>
    httpRequest<NotificationPage>(`/api/notifications?page=${page}&size=${size}&unreadOnly=${unreadOnly}`),
  unreadCount: () => httpRequest<{unread: number}>('/api/notifications/unread-count'),
  read: (id: number) => httpRequest<{updated: boolean}>(`/api/notifications/${id}/read`, { method: 'PATCH' }),
  readAll: () => httpRequest<{updated: number}>('/api/notifications/read-all', { method: 'PATCH' }),
  preferences: () => httpRequest<{marketingEnabled: boolean}>('/api/notifications/preferences'),
  updatePreferences: (marketingEnabled: boolean) =>
    httpRequest<{marketingEnabled: boolean}>('/api/notifications/preferences', {method:'PUT', body:JSON.stringify({marketingEnabled})}),
  sendCampaign: (target: string, category: string, title: string, body: string) =>
    httpRequest<{recipients: number}>('/api/notifications/admin/campaigns', {
      method:'POST', body: JSON.stringify({target,category,title,body}),
    }),
};
export function internalNotificationPath(path: string): string {
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//') || path.length > 260) {
    return '/notifications';
  }
  return Array.from(path).some(char => char === '\\' || char.charCodeAt(0) < 32) ? '/notifications' : path;
}
