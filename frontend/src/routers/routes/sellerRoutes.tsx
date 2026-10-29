import type { RouteObject } from 'react-router-dom';
import { ROLES } from '../../constants/roles';
import { ROUTES } from '../../constants/routes';
import { SiteLayout } from '../../layouts/SiteLayout';
import { SellerDashboardPage } from '../../pages/seller/SellerDashboardPage';
import { SellerOrderDetailPage } from '../../pages/seller/SellerOrderDetailPage';
import { SellerOrdersPage } from '../../pages/seller/SellerOrdersPage';
import { SellerProductEditPage } from '../../pages/seller/SellerProductEditPage';
import { SellerProductNewPage } from '../../pages/seller/SellerProductNewPage';
import { SellerProductsPage } from '../../pages/seller/SellerProductsPage';
import { SellerRequestsPage } from '../../pages/seller/SellerRequestsPage';
import { SellerProfilePage } from '../../pages/seller/SellerProfilePage';
import { SellerRevenuePage } from '../../pages/seller/SellerRevenuePage';
import { SellerSchedulePage } from '../../pages/seller/SellerSchedulePage';
import { ProtectedRoute } from '../guards/ProtectedRoute';
import { SellerDemoProvider } from '../../contexts/SellerDemoContext';

export const sellerRoutes: RouteObject[] = [
  {
    element: (
      <ProtectedRoute role={ROLES.SELLER}>
        <SiteLayout />
      </ProtectedRoute>
    ),
    children: [
      { path: ROUTES.SELLER_REQUESTS, element: <SellerRequestsPage /> },
      { path: ROUTES.SELLER_PROFILE, element: <SellerProfilePage /> },
      { path: ROUTES.SELLER, element: <SellerDashboardPage /> },
      { path: ROUTES.SELLER_ORDER_DETAIL, element: <SellerOrderDetailPage /> },
      { path: ROUTES.SELLER_ORDERS, element: <SellerOrdersPage /> },
      { path: ROUTES.SELLER_PRODUCT_EDIT, element: <SellerProductEditPage /> },
      { path: ROUTES.SELLER_PRODUCT_NEW, element: <SellerProductNewPage /> },
      { path: ROUTES.SELLER_PRODUCTS, element: <SellerProductsPage /> },
      { path: ROUTES.SELLER_REVENUE, element: <SellerRevenuePage /> },
      { path: ROUTES.SELLER_SCHEDULE, element: <SellerSchedulePage /> },
    ],
  },
];
