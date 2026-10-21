import {httpRequest} from './httpClient';
import type {
    AdminProduct,
    AdminUser,
    DashboardResponse,
    OrdersResponse,
    ProductsResponse,
    StatisticsResponse,
    UsersResponse,
    AdminOrder,
    RevenueGroupBy,
} from '../types/admin';

function query(params: Record<string, string | number | undefined | null>): string {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null) search.set(key, String(value));
    });
    return search.toString();
}

export const adminClient = {
    dashboard: () => httpRequest<DashboardResponse>('/api/admin/dashboard'),

    users: (params: { q?: string; role?: string; status?: string; page?: number; size?: number }) =>
        httpRequest<UsersResponse>(`/api/admin/users?${query(params)}`),

    user: (id: string | number) =>
        httpRequest<{ user: AdminUser; roles: string[] }>(`/api/admin/users/${id}`),

    updateUser: (id: string | number, payload: { username: string; roles: string[]; active: boolean }) =>
        httpRequest<{ message: string; user: AdminUser }>(`/api/admin/users/${id}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        }),

    toggleUser: (id: string | number) =>
        httpRequest<{ message: string; user: AdminUser }>(`/api/admin/users/${id}/toggle`, {method: 'PATCH'}),

    products: (params: { q?: string; category?: string; status?: string; page?: number; size?: number }) =>
        httpRequest<ProductsResponse>(`/api/admin/products?${query(params)}`),

    productOptions: () =>
        httpRequest<{ categories: string[]; statuses: string[] }>('/api/admin/products/options'),

    product: (id: string | number) =>
        httpRequest<{ product: AdminProduct; categories: string[]; statuses: string[] }>(`/api/admin/products/${id}`),

    createProduct: (payload: Omit<AdminProduct, 'id'>) =>
        httpRequest<{ message: string; product: AdminProduct }>('/api/admin/products', {
            method: 'POST',
            body: JSON.stringify(payload),
        }),

    updateProduct: (id: string | number, payload: Omit<AdminProduct, 'id'>) =>
        httpRequest<{ message: string; product: AdminProduct }>(`/api/admin/products/${id}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        }),

    toggleProduct: (id: string | number) =>
        httpRequest<{ message: string; product: AdminProduct }>(`/api/admin/products/${id}/toggle`, {method: 'PATCH'}),

    orders: (params: { q?: string; status?: string; from?: string; to?: string; page?: number; size?: number }) =>
        httpRequest<OrdersResponse>(`/api/admin/orders?${query(params)}`),

    order: (id: string | number) =>
        httpRequest<{ order: AdminOrder; allowedStatuses: string[] }>(`/api/admin/orders/${id}`),

    updateOrder: (id: string | number, payload: { status: string; internalNote: string }) =>
        httpRequest<{ message: string; order: AdminOrder; allowedStatuses: string[] }>(`/api/admin/orders/${id}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        }),

    statistics: (from: string, to: string, groupBy: RevenueGroupBy,) =>
        httpRequest<StatisticsResponse>(
            `/api/admin/statistics?${query({from, to, groupBy,})}`,
        ),
};
