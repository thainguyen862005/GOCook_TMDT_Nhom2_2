import type { RouteObject } from 'react-router-dom';
import { ROLES } from '../../constants/roles';
import { ROUTES } from '../../constants/routes';
import { AdminDashboardPage } from '../../pages/admin/DashboardPage';
import { AdminOrderDetailPage } from '../../pages/admin/OrderDetailPage';
import { AdminOrdersPage } from '../../pages/admin/OrdersPage';
import { AdminProductEditPage, AdminProductNewPage } from '../../pages/admin/ProductFormPage';
import { AdminProductsPage } from '../../pages/admin/ProductsPage';
import { AdminStatisticsPage } from '../../pages/admin/StatisticsPage';
import { AdminSiteSettingsPage } from '../../pages/admin/SiteSettingsPage';
import { AdminOperationsPage } from '../../pages/admin/OperationsPage';
import { FaqManagementPage } from '../../pages/admin/FaqManagementPage';
import { AdminCookingRequestsPage } from '../../pages/admin/CookingRequestsPage';
import { AdminUserEditPage } from '../../pages/admin/UserEditPage';
import { AdminUsersPage } from '../../pages/admin/UsersPage';
import { ProtectedRoute } from '../guards/ProtectedRoute';

export const adminRoutes: RouteObject[] = [
  {
    element: <ProtectedRoute role={ROLES.ADMIN} />,
    children: [
      { path: ROUTES.ADMIN, element: <AdminDashboardPage /> },
      { path: ROUTES.ADMIN_USERS, element: <AdminUsersPage /> },
      { path: ROUTES.ADMIN_USER_EDIT, element: <AdminUserEditPage /> },
      { path: ROUTES.ADMIN_PRODUCTS, element: <AdminProductsPage /> },
      { path: ROUTES.ADMIN_PRODUCT_NEW, element: <AdminProductNewPage /> },
      { path: ROUTES.ADMIN_PRODUCT_EDIT, element: <AdminProductEditPage /> },
      { path: ROUTES.ADMIN_ORDERS, element: <AdminOrdersPage /> },
      { path: ROUTES.ADMIN_ORDER_DETAIL, element: <AdminOrderDetailPage /> },
      { path: ROUTES.ADMIN_STATISTICS, element: <AdminStatisticsPage /> },
      { path: ROUTES.ADMIN_SITE_SETTINGS, element: <AdminSiteSettingsPage /> },
      { path: ROUTES.ADMIN_OPERATIONS, element: <AdminOperationsPage /> },
      { path: ROUTES.ADMIN_FAQS, element: <FaqManagementPage /> },
      { path: ROUTES.ADMIN_COOKING_REQUESTS, element: <AdminCookingRequestsPage /> },
    ],
  },
];
