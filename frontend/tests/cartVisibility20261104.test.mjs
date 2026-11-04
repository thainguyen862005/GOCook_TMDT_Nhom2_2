import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const header = readFileSync(new URL('../src/layouts/SiteLayout.tsx', import.meta.url), 'utf8');
const menu = readFileSync(new URL('../src/pages/menu/MenuDetailPage.tsx', import.meta.url), 'utf8');
const css = readFileSync(new URL('../src/styles/globals.css', import.meta.url), 'utf8');

test('cart has a visible icon entry for public visitors and customers, but not admin or chef', () => {
  assert.match(header, /!isAdmin\s*&&\s*!isSeller\s*&&\s*<Link className="header-cart-link" to=\{ROUTES\.CART\}/);
  assert.match(header, /aria-label="Mở giỏ hàng"/);
  assert.match(header, /<span>Giỏ hàng<\/span>/);
  assert.match(header, /!isAdmin\s*&&\s*!isSeller\s*&&\s*<Link to=\{ROUTES\.CART\}>Giỏ hàng<\/Link>/);
});

test('cart link remains prominent on desktop and accessible on mobile', () => {
  assert.match(css, /\.header-cart-link\s*\{[^}]*display:\s*inline-flex/);
  assert.match(css, /\.header-cart-link span\s*\{\s*display:\s*none/);
});

test('menu detail contains add-to-cart action and persistent link to cart', () => {
  assert.match(menu, /cartClient\.add\(data\.id,selectedGuests\)/);
  assert.match(menu, /Thêm vào giỏ hàng/);
  assert.match(menu, /Xem giỏ hàng của tôi/);
});
