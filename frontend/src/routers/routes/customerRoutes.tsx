import { Navigate } from 'react-router-dom';
import type { RouteObject } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import { SiteLayout } from '../../layouts/SiteLayout';
import { AccountPage } from '../../pages/account/AccountPage';
import { NotificationCenterPage } from '../../pages/notifications/NotificationCenterPage';
import { CheckoutPage } from '../../pages/checkout/CheckoutPage';
import { CheckoutSuccessPage } from '../../pages/checkout/CheckoutSuccessPage';
import { PaymentResultPage } from '../../pages/checkout/PaymentResultPage';
import { VegetarianCheckoutPage } from '../../pages/checkout/VegetarianCheckoutPage';
import { VegetarianCheckoutSuccessPage } from '../../pages/checkout/VegetarianCheckoutSuccessPage';
import { OrderDetailPage } from '../../pages/order/OrderDetailPage';
import { OrdersPage } from '../../pages/order/OrdersPage';
import { TrackingPage } from '../../pages/order/TrackingPage';
import { CookingRequestPage } from '../../pages/request/CookingRequestPage';
import { ChatPage } from '../../pages/support/ChatPage';
import { MessagesPage } from '../../pages/support/MessagesPage';
import { MyRequestsPage } from '../../pages/request/MyRequestsPage';
import { RequestDetailPage } from '../../pages/request/RequestDetailPage';
import { QuotesPage } from '../../pages/request/QuotesPage';
import { ProtectedRoute } from '../guards/ProtectedRoute';
import { VerifiedEmailRoute } from '../guards/VerifiedEmailRoute';

export const customerRoutes: RouteObject[] = [
  {
    element: (
      <ProtectedRoute>
        <SiteLayout />
      </ProtectedRoute>
    ),
    children: [
      { path: ROUTES.ACCOUNT, element: <AccountPage /> },
      { path: ROUTES.NOTIFICATIONS, element: <NotificationCenterPage /> },
      { path: ROUTES.MY_REQUESTS, element: <MyRequestsPage /> },
      { path: ROUTES.REQUEST_DETAIL, element: <RequestDetailPage /> },
      { path: ROUTES.CHAT, element: <ChatPage /> },
      { path: ROUTES.MESSAGES, element: <MessagesPage /> },
      { path: ROUTES.CART, element: <Navigate to={ROUTES.MENUS} replace /> },
      { path: ROUTES.CART_EMPTY, element: <Navigate to={ROUTES.MENUS} replace /> },
      { path: ROUTES.CART_VEGETARIAN, element: <Navigate to={ROUTES.MENUS} replace /> },
      {
        path: ROUTES.CHECKOUT,
        element: <VerifiedEmailRoute><CheckoutPage /></VerifiedEmailRoute>,
      },
      {
        path: ROUTES.CHECKOUT_VEGETARIAN,
        element: <VerifiedEmailRoute><VegetarianCheckoutPage /></VerifiedEmailRoute>,
      },
      {
        path: ROUTES.REQUEST,
        element: <VerifiedEmailRoute><CookingRequestPage /></VerifiedEmailRoute>,
      },
      {
        path: ROUTES.QUOTES,
        element: <VerifiedEmailRoute><QuotesPage /></VerifiedEmailRoute>,
      },
      { path: ROUTES.ORDER_DETAIL, element: <OrderDetailPage /> },
      { path: ROUTES.ORDERS, element: <OrdersPage /> },
      { path: ROUTES.CHECKOUT_SUCCESS, element: <CheckoutSuccessPage /> },
      { path: ROUTES.CHECKOUT_VEGETARIAN_SUCCESS, element: <VegetarianCheckoutSuccessPage /> },
      { path: ROUTES.TRACKING, element: <TrackingPage /> },
      { path: ROUTES.PAYMENT_RESULT, element: <PaymentResultPage /> },
    ],
  },
];
