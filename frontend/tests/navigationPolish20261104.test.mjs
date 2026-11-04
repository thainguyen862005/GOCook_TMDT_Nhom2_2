import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const header = readFileSync(new URL('../src/layouts/SiteLayout.tsx', import.meta.url), 'utf8');
const home = readFileSync(new URL('../src/pages/home/HomePage.tsx', import.meta.url), 'utf8');
const chef = readFileSync(new URL('../src/components/chef/ChefCard.tsx', import.meta.url), 'utf8');
const css = readFileSync(new URL('../src/styles/globals.css', import.meta.url), 'utf8');

test('compact header icons retain accessible names, logout and customer cart route', () => {
  assert.match(header, /aria-label="Tin nhắn"/);
  assert.match(header, /aria-label="Mở giỏ hàng"/);
  assert.match(header, /aria-label="Trang quản trị"/);
  assert.match(header, /aria-label="Kênh đầu bếp"/);
  assert.match(header, /aria-label="Đăng xuất"/);
  assert.match(header, /profile && <Link className="session-logout-link/);
  assert.match(header, /ROUTES\.CART/);
  assert.match(header, /ROUTES\.MY_REPORTS/);
  assert.match(css, /\.header-actions \.header-cart-link span\s*\{\s*display:\s*none/);
});

test('recommended menus and top chefs retain live data features', () => {
  assert.match(home, /sort:\s*'bookings'/);
  assert.match(home, /menuClient\.featured\(/);
  assert.match(home, /menu\.featuredReason/);
  assert.match(chef, /chef\.bookingCount/);
});
