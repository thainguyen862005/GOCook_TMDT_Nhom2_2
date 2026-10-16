/**
 * Central route registry. Router files decide which role/domain owns each route.
 * Pages/components should import paths from here when they need to navigate.
 */
export const ROUTES = {
  HOME: '/',

  LOGIN: '/login',
  REGISTER: '/register',
  VERIFY_EMAIL: '/verify-email',
  FORGOT_PASSWORD: '/forgot-password',
  RESET_PASSWORD: '/reset-password',
  LOGOUT: '/logout',

  ACCOUNT: '/account',

  CHEFS: '/chefs',
  CHEF_DETAIL: '/chef-detail',
  CHEF_AN_NHIEN: '/chef-an-nhien',

  MENUS: '/menus',
  MENU_DETAIL: '/menu-detail',
  MENU_VEGETARIAN: '/menu-vegetarian',

  OFFERS: '/offers',
  HELP: '/help',
  REQUEST: '/request',
  PAGE_CATALOG: '/pages',
  QUOTES: '/quotes',
  REVIEW: '/review',
  MATCHING: '/matching',
  BECOME_CHEF: '/become-chef',
  CHAT: '/chat',

  CART: '/cart',
  CART_EMPTY: '/cart-empty',
  CART_VEGETARIAN: '/cart-vegetarian',
  CHECKOUT: '/checkout',
  CHECKOUT_VEGETARIAN: '/checkout-vegetarian',
  ORDER_DETAIL: '/order-detail',
  ORDERS: '/orders',
  CHECKOUT_SUCCESS: '/success',
  CHECKOUT_VEGETARIAN_SUCCESS: '/success-vegetarian',
  TRACKING: '/tracking',
  PAYMENT_RESULT: '/payment-result',

  SELLER: '/seller',
  SELLER_ORDER_DETAIL: '/seller/order-detail',
  SELLER_ORDERS: '/seller/orders',
  SELLER_PRODUCT_EDIT: '/seller/product-edit',
  SELLER_PRODUCT_NEW: '/seller/product-new',
  SELLER_PRODUCTS: '/seller/products',
  SELLER_REVENUE: '/seller/revenue',
  SELLER_SCHEDULE: '/seller/schedule',

  ADMIN: '/admin',
  ADMIN_USERS: '/admin/users',
  ADMIN_USER_EDIT: '/admin/user-edit',
  ADMIN_PRODUCTS: '/admin/products',
  ADMIN_PRODUCT_NEW: '/admin/product-new',
  ADMIN_PRODUCT_EDIT: '/admin/product-edit',
  ADMIN_ORDERS: '/admin/orders',
  ADMIN_ORDER_DETAIL: '/admin/order-detail',
  ADMIN_STATISTICS: '/admin/statistics',
} as const;
