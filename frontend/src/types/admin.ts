import type {PageResponse} from './api';

export interface AdminUser {
    id: number;
    username: string;
    email: string;
    role: string;
    roles?: string[];
    roleLabel?: string;
    active: boolean;
    createdAt?: number;
    createdAtDisplay?: string;
}

export interface AdminProduct {
    id: number;
    version?: number;
    name: string;
    category: string;
    unitPrice: number;
    minGuests: number;
    maxGuests: number;
    dishItems?: string | null;
    description?: string | null;
    imageUrl?: string | null;
    status: string;
    sellerId?: number | null;
    displayCode?: string;
}

export interface AdminOrder {
    id: number;
    orderCode: string;
    customerName: string;
    customerEmail?: string;
    productName: string;
    chefName?: string;
    serviceAddress?: string;
    guestCount: number;
    scheduledAt?: string;
    scheduledAtDisplay?: string;
    totalAmount: number;
    status: string;
    customerNote?: string;
    internalNote?: string;
}

export interface DashboardResponse {
    userCount: number;
    productCount: number;
    orderCount: number;
    activeChefCount: number;

    todayRevenue: number;
    thisMonthRevenue: number;
    thisYearRevenue: number;
    completedRevenue: number;

    recentOrders: AdminOrder[];
}

export interface UsersResponse extends PageResponse<AdminUser> {
    roles: string[];
}

export interface ProductsResponse extends PageResponse<AdminProduct> {
    categories: string[];
    statuses: string[];
}

export interface OrdersResponse extends PageResponse<AdminOrder> {
    statuses: string[];
}

export type RevenueGroupBy = | 'DAY' | 'MONTH' | 'YEAR';

export interface StatisticsResponse {
    from: string;
    to: string;
    groupBy: RevenueGroupBy;

    revenue: number;
    orderCount: number;
    chefCount: number;
    userCount: number;

    revenueSeries: Array<{
        key: string;
        label: string;
        revenue: number;
        completedOrders: number;
    }>;

    statusCounts: Record<string, number>;
}
