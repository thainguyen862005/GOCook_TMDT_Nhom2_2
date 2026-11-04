import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=p=>readFileSync(new URL(p,import.meta.url),'utf8');
test('customer cart is server owned; no fake redirects or hardcoded prices',()=>{
  const routes=read('../src/routers/routes/customerRoutes.tsx');
  const cart=read('../src/pages/cart/CartPage.tsx');
  const api=read('../../src/main/java/com/james/LMS/publiccontent/CustomerCartController.java');
  assert.match(routes,/path: ROUTES\.CART, element: <CartPage/);
  assert.match(cart,/cartClient\.add|cartClient\.update/); // add is from menu, update from cart
  assert.match(cart,/cartClient\.remove/);
  assert.match(api,/c\.user_id=:uid/);
  assert.match(api,/p\.status='PUBLISHED'/);
  assert.match(api,/guest_count/);
});
test('checkout keeps original payment and escrow contract, stores purchased item for review',()=>{
  const payment=read('../../src/main/java/com/james/LMS/service/impl/PaymentServiceImpl.java');
  assert.match(payment,/INSERT INTO order_items\(order_id,product_id/);
  assert.match(payment,/pricing\.quoteFixedMenu/);
  assert.match(payment,/idempotencyKey\(\)/);
});
test('product reviews cannot be created from an unverified order',()=>{
  const review=read('../../src/main/java/com/james/LMS/publiccontent/VerifiedMenuReviewController.java');
  assert.match(review,/o\.customer_id=:user/);
  assert.match(review,/o\.status='COMPLETED'/);
  assert.match(review,/e\.status='RELEASED'/);
  assert.match(review,/JOIN order_items oi ON oi\.order_id=o\.id/);
  assert.match(review,/ON CONFLICT\(order_id\) DO NOTHING/);
});
test('menu questions are unpublished until answered by owning chef',()=>{
  const faq=read('../../src/main/java/com/james/LMS/publiccontent/MenuQuestionController.java');
  assert.match(faq,/q\.status='ANSWERED'/);
  assert.match(faq,/chef_id=:chef AND status='PENDING'/);
  assert.match(faq,/hasAuthority\('ROLE_INSTRUCTOR'\)/);
  assert.match(faq,/hasAuthority\('ROLE_SYSTEM_ADMIN'\)/);
});
test('reports are tied to real targets and Chef cannot pick an arbitrary customer',()=>{
  const api=read('../../src/main/java/com/james/LMS/publiccontent/ContentReportController.java');
  assert.match(api,/id=:id AND chef_id=:chef/);
  assert.match(api,/status IN \('OPEN','IN_REVIEW'\)/);
  assert.match(api,/hasAuthority\('ROLE_SYSTEM_ADMIN'\)/);
  assert.match(api,/resolved_by=:admin/);
});
test('popular chefs are ordered by completed and settled bookings, not rating alone',()=>{
  const api=read('../../src/main/java/com/james/LMS/publiccontent/PublicChefService.java');
  const home=read('../src/pages/home/HomePage.tsx');
  assert.match(api,/b\.booking_count DESC/);
  assert.match(api,/o\.status='COMPLETED'/);
  assert.match(api,/e\.status='RELEASED'/);
  assert.match(home,/sort: 'bookings'/);
});
test('featured menus are public and ordered by completed settled booking counts without ML',()=>{
  const endpoint=read('../../src/main/java/com/james/LMS/publiccontent/FeaturedMenuController.java');
  const client=read('../src/clients/menuClient.ts');
  const home=read('../src/pages/home/HomePage.tsx');
  assert.match(endpoint,/COUNT\(DISTINCT o\.id\)/);
  assert.match(endpoint,/o\.status = 'COMPLETED'/);
  assert.match(endpoint,/e\.status = 'RELEASED'/);
  assert.match(endpoint,/p\.status = 'PUBLISHED'/);
  assert.match(endpoint,/ORDER BY COALESCE\(b\.booking_count, 0\) DESC/);
  assert.match(client,/api\/public\/featured-menus/);
  assert.match(home,/menuClient\.featured\(abort\.signal\)/);
});
test('help remains a human FAQ and support ticket, without an automated chatbot',()=>{
  const help=read('../src/pages/support/HelpPage.tsx');
  const client=read('../src/clients/publicContentClient.ts');
  const security=read('../../src/main/java/com/james/LMS/config/SecurityConfig.java');
  assert.match(help,/publicContentClient\.faqs\(abort\.signal\)/);
  assert.match(help,/publicContentClient\.ticket\(/);
  assert.doesNotMatch(help,/publicContentClient\.assistant|Chatbot hỗ trợ nhanh|botQuestion/);
  assert.doesNotMatch(client,/api\/public\/support\/assistant/);
  assert.doesNotMatch(security,/api\/public\/support\/assistant|api\/public\/recommendations/);
});
test('public featured menus, public product reviews, and answered Q&A are reachable without a session',()=>{
  const util=read('../../src/main/java/com/james/LMS/util/PublicEndpointsValidatorUtil.java');
  const security=read('../../src/main/java/com/james/LMS/config/SecurityConfig.java');
  assert.match(util,/"\/api\/public\/featured-menus"/);
  assert.match(util,/"\/api\/public\/menus\/"/);
  assert.match(security,/"\/api\/public\/menus\/\*\*"/);
  assert.match(security,/"\/api\/public\/featured-menus"/);
  assert.doesNotMatch(util,/"\/api\/customer\/cart"/);
});
test('admin donut uses 4 mutually exclusive groups and zero-data fallback',()=>{
  const ui=read('../src/pages/admin/StatisticsPage.tsx');
  assert.match(ui,/chart\.inProgress/);
  assert.match(ui,/chart\.actualTotal===0/);
  assert.match(ui,/Đã từ chối/);
  assert.match(ui,/Hoàn thành/);
});
test('new API forms are reachable from customer, chef and admin routes',()=>{
  const admin=read('../src/routers/routes/adminRoutes.tsx');
  const seller=read('../src/routers/routes/sellerRoutes.tsx');
  const customer=read('../src/routers/routes/customerRoutes.tsx');
  assert.match(admin,/ROUTES\.ADMIN_QUESTIONS/);
  assert.match(admin,/ROUTES\.ADMIN_CONTENT_REPORTS/);
  assert.match(seller,/ROUTES\.SELLER_QUESTIONS/);
  assert.match(customer,/ROUTES\.CART/);
  assert.match(customer,/ROUTES\.MY_REPORTS/);
});

test('cart item is removed only after confirmed payment and escrow, never on gateway failure',()=>{
  const checkout=read('../src/hooks/checkout/useCheckout.ts');
  const paymentResult=read('../src/pages/checkout/PaymentResultPage.tsx');
  assert.match(checkout,/gocook:cart-order:/);
  assert.match(paymentResult,/view !== 'success'/);
  assert.match(paymentResult,/cartClient\.remove\(itemId\)/);
  assert.doesNotMatch(checkout,/cartClient\.remove\(/);
});
