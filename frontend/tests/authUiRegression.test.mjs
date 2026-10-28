// Lightweight source-level regression checks; not a substitute for browser/E2E tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = (path) => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8');

test('Admin exposes logout in both header and sticky sidebar', () => {
  const s = source('layouts/AdminLayout.tsx');
  assert.match(s, /admin-logout-action.*to=\{ROUTES\.LOGOUT\}/);
  assert.match(s, /admin-side-logout.*to=\{ROUTES\.LOGOUT\}/);
});

test('Google provider is visible even if Google configuration or SDK fails', () => {
  const s = source('components/auth/GoogleAuthButton.tsx');
  assert.match(s, /function GoogleMark\(/);
  assert.match(s, /<GoogleMark\s*\/>/);
  assert.match(s, /Đăng nhập Google chưa được cấu hình/);
  assert.match(s, /Lấy mã mới và thử lại bằng Google/);
  assert.doesNotMatch(s, /if\s*\(!clientId\)\s*return null/);
});

test('Logout failure is visible and can be retried, without a fake success', () => {
  const page = source('pages/auth/LogoutPage.tsx');
  const state = source('contexts/AuthContext.tsx');
  assert.match(page, /status === 'error'/);
  assert.match(page, /Thử đăng xuất lại/);
  assert.match(state, /await authClient\.logout\(\);\s*authRequestEpoch\.current \+= 1;\s*setProfile\(null\)/);
  assert.doesNotMatch(state, /finally\s*\{\s*setProfile\(null\)/);
  assert.match(state, /authRequestEpoch\.current \+= 1/);
});

test('Public desktop and mobile offer sign-out when authenticated', () => {
  const s = source('layouts/SiteLayout.tsx');
  assert.match(s, /profile && <Link className="session-logout-link/);
  assert.match(s, /profile \? ROUTES\.LOGOUT : ROUTES\.LOGIN/);
});
