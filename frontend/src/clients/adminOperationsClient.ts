import { httpRequest } from './httpClient';

export type AdminOpsModule =
  | 'customers' | 'chefs' | 'payments' | 'vouchers' | 'cases'
  | 'refunds' | 'reviews' | 'tickets' | 'audit';
export type AdminRow = Record<string, unknown>;
export interface AdminOpsPage {
  content: AdminRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

function path(resource: AdminOpsModule) {
  return `/api/admin/ops/${resource}`;
}

export const adminOperationsClient = {
  list: (resource: AdminOpsModule, params: { q?: string; status?: string; page?: number; size?: number }) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) query.set(key, String(value));
    });
    return httpRequest<AdminOpsPage>(`${path(resource)}?${query.toString()}`);
  },
  detail: (resource: AdminOpsModule, id: number) =>
    httpRequest<AdminRow>(`${path(resource)}/${id}`),
  profile: (resource: 'chefs' | 'customers', id: number) =>
    httpRequest<AdminRow>(`/api/admin/ops/profiles/${resource}/${id}`),
  orderTimeline: (id: number) =>
    httpRequest<AdminRow>(`/api/admin/ops/orders/${id}/timeline`),
  summary: () =>
    httpRequest<Array<{ metric: string; value: number }>>('/api/admin/ops/summary'),
  create: (resource: AdminOpsModule, payload: Record<string, unknown>) =>
    httpRequest<AdminRow>(path(resource), { method: 'POST', body: JSON.stringify(payload) }),
  update: (resource: AdminOpsModule, id: number, payload: Record<string, unknown>) =>
    httpRequest<AdminRow>(`${path(resource)}/${id}`, {
      method: 'PATCH', body: JSON.stringify(payload),
    }),
  verifyChef: (id: number, status: string, reason: string) =>
    httpRequest<AdminRow>(`/api/admin/ops/chefs/${id}/verify`, {
      method: 'PATCH', body: JSON.stringify({ status, reason }),
    }),
  chefActive: (id: number, active: boolean, reason: string) =>
    httpRequest<AdminRow>(`/api/admin/ops/chefs/${id}/active`, {
      method: 'PATCH', body: JSON.stringify({ active, reason }),
    }),
};
