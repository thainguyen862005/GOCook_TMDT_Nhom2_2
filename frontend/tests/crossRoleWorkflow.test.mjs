import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const root = new URL('../src/', import.meta.url);
const read = name => readFileSync(new URL(name, root), 'utf8');
const backend = readFileSync(new URL('../../src/main/java/com/james/LMS/workflow/OrderChatController.java', import.meta.url), 'utf8');
test('chat and inbox routes are registered behind authentication', () => {
  const routes = read('routers/routes/customerRoutes.tsx');
  assert.match(routes, /path: ROUTES\.CHAT, element: <ChatPage \/>/);
  assert.match(routes, /path: ROUTES\.MESSAGES, element: <MessagesPage \/>/);
  assert.match(routes, /<ProtectedRoute>/);
});
test('Customer and Chef see links to the same order chat thread', () => {
  assert.match(read('pages/order/OrderDetailPage.tsx'), /ROUTES\.CHAT\}\?orderId=\$\{order\.id\}/);
  assert.match(read('pages/seller/SellerOrderDetailPage.tsx'), /ROUTES\.CHAT\}\?orderId=\$\{o\.id\}/);
  assert.match(read('pages/seller/SellerRequestsPage.tsx'), /ROUTES\.CHAT\}\?requestId=\$\{r\.id\}&peerId=\$\{r\.customerId\}/);
});
test('backend authorizes order messages against the order owner and assigned chef', () => {
  assert.match(backend, /o\.customer_id=:actor OR o\.chef_id=:actor/);
  assert.match(backend, /FROM order_messages m/);
  assert.match(backend, /INSERT INTO order_messages/);
  assert.match(backend, /notifications\.sendToUser\(/);
  assert.doesNotMatch(backend, /UPDATE\s+(orders|order_escrows|payment_transactions)/i);
});
test('account sidebar separates links by role and hash link cannot double-highlight', () => {
  const sidebar=read('components/account/AccountSidebar.tsx');
  assert.match(sidebar, /isAdmin, isSeller/);
  assert.match(sidebar, /<Link to=\{`\$\{ROUTES\.ACCOUNT\}#account-security`\}>/);
  assert.doesNotMatch(sidebar, /<NavLink to=\{`\$\{ROUTES\.ACCOUNT\}#account-security`\}/);
});
test('checkout communicates missing requirements rather than silently ignoring a click', () => {
  const hook=read('hooks/checkout/useCheckout.ts');
  assert.match(hook, /Hãy hoàn tất/);
  assert.match(hook, /redirectToVnpay\(result, result\.orderCode\)/);
  assert.match(hook, /pendingOrderCode\.current/);
});
