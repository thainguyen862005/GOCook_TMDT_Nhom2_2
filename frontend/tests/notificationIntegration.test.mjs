// Wiring regression tests (source-level); backend/service integration requires Maven and PostgreSQL.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const src = path => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8');

test('notification inbox uses per-account API, not client-supplied recipient ID', () => {
  const client = src('clients/notificationClient.ts');
  assert.match(client, /\/api\/notifications\/unread-count/);
  assert.match(client, /\/api\/notifications\/preferences/);
  assert.doesNotMatch(client, /recipientId\s*:/);
});

test('customer/chef inbox remains behind authentication', () => {
  const routes = src('routers/routes/customerRoutes.tsx');
  assert.match(routes, /<ProtectedRoute>/);
  assert.match(routes, /ROUTES\.NOTIFICATIONS, element: <NotificationCenterPage/);
  const seller = src('routers/routes/sellerRoutes.tsx');
  assert.match(seller, /<SiteLayout\s*\/>/);
  assert.match(src('layouts/SiteLayout.tsx'), /<NotificationBell\s*\/>/);
});

test('admin inbox and campaign UI are linked from admin navigation', () => {
  const routes = src('routers/routes/adminRoutes.tsx');
  assert.match(routes, /<ProtectedRoute role=\{ROLES\.ADMIN\}/);
  assert.match(routes, /ROUTES\.ADMIN_NOTIFICATIONS, element: <AdminNotificationCampaignPage/);
  const layout = src('layouts/AdminLayout.tsx');
  assert.match(layout, /ROUTES\.ADMIN_NOTIFICATIONS/);
  assert.match(layout, /<NotificationBell\s*\/>/);
});

test('notifications route is never declared in public auth whitelist', () => {
  const routes = src('constants/routes.ts');
  assert.match(routes, /NOTIFICATIONS: '\/notifications'/);
  assert.match(routes, /ADMIN_NOTIFICATIONS: '\/admin\/notifications'/);
});
