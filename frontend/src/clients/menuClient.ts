import { httpRequest } from './httpClient';
import type { MenuDetail, MenuSummary } from '../types/menu';
import type { PageResponse } from '../types/api';

const path = '/api/products';

export interface MenuSearchParams {
  q?: string;
  category?: string;
  minPrice?: string;
  maxPrice?: string;
  sort?: string;
  page?: number;
  size?: number;
  chef?: string;
}

export const menuClient = {
  get: (id: string, signal?: AbortSignal) =>
    httpRequest<MenuDetail>(`${path}/${encodeURIComponent(id)}`, { signal }),
  list: (filters: MenuSearchParams, signal?: AbortSignal) => {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== '') params.set(key, String(value));
    });
    return httpRequest<PageResponse<MenuSummary>>(`${path}?${params}`, { signal });
  },
  categories: (signal?: AbortSignal) =>
    httpRequest<string[]>(`${path}/categories`, { signal }),
};
