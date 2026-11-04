import { httpRequest } from './httpClient';
import type { RecommendedMenu } from '../types/recommendation';

export const recommendationClient = {
  list: (signal?: AbortSignal) =>
    httpRequest<RecommendedMenu[]>('/api/customer/recommendations?size=3&k=5', { signal }),
  similar: (menuId: number, signal?: AbortSignal) =>
    httpRequest<RecommendedMenu[]>(`/api/public/menus/${menuId}/similar?size=3`, { signal }),
};
