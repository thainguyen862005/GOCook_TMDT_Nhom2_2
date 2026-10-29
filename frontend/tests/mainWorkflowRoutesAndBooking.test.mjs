import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const ui = path => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8');
const backend = path => readFileSync(new URL(`../../src/main/java/com/james/LMS/${path}`, import.meta.url), 'utf8');

test('customer matching, chat and my requests resolve to real pages, not home wildcard', () => {
  const routes = ui('routers/routes/customerRoutes.tsx');
  for (const [constant, component] of [
    ['MY_REQUESTS', 'MyRequestsPage'],
    ['REQUEST_DETAIL', 'RequestDetailPage'],
    ['CHAT', 'ChatPage'],
  ]) {
    assert.match(routes, new RegExp(`path: ROUTES\\.${constant}, element: <${component} \\/>`));
  }
  const router = ui('routers/AppRouter.tsx');
  assert.doesNotMatch(router, /path: '\*', element: <Navigate to=\{ROUTES\.HOME\}/);
  assert.match(router, /<NotFoundPage/);
});

test('fixed-menu checkout includes Chef booking and does not mark payment as successful', () => {
  const payment = backend('service/impl/PaymentServiceImpl.java');
  const method = payment.slice(payment.indexOf('public PaymentCheckoutResponse checkout('), payment.indexOf('public PaymentCheckoutResponse checkoutOrder('));
  assert.match(method, /INSERT INTO chef_bookings\(order_id,chef_id,starts_at,ends_at,booking_status\)/);
  assert.match(method, /DataIntegrityViolationException/);
  assert.match(method, /openPayment\(/);
  assert.match(method, /\.status\(OrderStatus\.PENDING\)/);
  assert.doesNotMatch(method, /\.status\(OrderStatus\.CONFIRMED\)/);
});

test('payment retry keeps existing order and freezes fields after order creation', () => {
  const hook = ui('hooks/checkout/useCheckout.ts');
  const page = ui('pages/checkout/CheckoutView.tsx');
  assert.match(hook, /if \(pendingOrderCode\.current\)/);
  assert.match(hook, /setCreatedOrderCode\(result\.orderCode\)/);
  assert.match(page, /disabled=\{Boolean\(checkout\.createdOrderCode\)\}/);
  assert.match(page, /min=\{checkout\.today\}/);
});

test('Chef and customer are notified about matching, offers and request chat', () => {
  const request = backend('publiccontent/CookingRequestController.java');
  const workflow = backend('workflow/ServiceWorkflowController.java');
  assert.match(request, /request:matched:/);
  assert.match(request, /\/request-detail\?id=/);
  assert.match(workflow, /request:message:/);
  assert.match(workflow, /\/chat\?requestId=/);
  assert.match(workflow, /notifications\.sendToUser\(input\.chefId\(\)/);
});

test('profile keeps a single, responsive main content column', () => {
  const page = ui('pages/account/AccountPage.tsx');
  const css = ui('styles/globals.css');
  assert.match(page, /account-workspace/);
  assert.match(page, /account-section-heading/);
  assert.match(css, /\.account-workspace \{ grid-template-columns/);
  assert.match(css, /@media \(max-width: 900px\) \{\s*\.account-workspace \{ grid-template-columns: 1fr/);
});
