import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const read = file => readFileSync(new URL(file, import.meta.url), 'utf8');

test('homepage uses vertical featured Chef card while catalog preserves horizontal variant', () => {
  const home = read('../src/pages/home/HomePage.tsx');
  const card = read('../src/components/chef/ChefCard.tsx');
  const style = read('../src/styles/globals.css');
  assert.match(home, /<ChefCard key=\{chef\.id\} chef=\{chef\} compact\s*\/>/);
  assert.match(card, /compact \? 'chef-card--featured' : ''/);
  assert.match(style, /\.chef-card--featured\s*\{[^}]*flex-direction:\s*column/);
  assert.match(style, /\.chef-card--featured\s*>\s*img\s*\{[^}]*object-fit:\s*cover/);
  assert.match(card, /onError=/);
});

test('Chef orders use backend filtering, controlled pagination, and authenticated scope', () => {
  const page = read('../src/pages/seller/SellerOrdersPage.tsx');
  const api = read('../src/clients/chefOrderClient.ts');
  const controller = read('../../src/main/java/com/james/LMS/seller/orders/ChefOrderController.java');
  const service = read('../../src/main/java/com/james/LMS/seller/orders/ChefOrderService.java');
  assert.match(page, /chefOrderClient\.list\(\{ q: search, status, serviceType, from, to, page, size: PAGE_SIZE \}/);
  assert.match(page, /<SellerPagination page=\{page\} totalPages=/);
  assert.match(api, /q\?:string;serviceType\?:string/);
  assert.match(controller, /service\.list\(actor\.getId\(\),status,q,serviceType,page,size,from,to\)/);
  assert.match(service, /WHERE o\.chef_id=\? AND o\.is_active=true/);
  assert.match(service, /args\.add\(keyword\)/);
});

test('Chef requests are paginated and polling does not reset an offer form while sending', () => {
  const page = read('../src/pages/seller/SellerRequestsPage.tsx');
  assert.match(page, /const PAGE_SIZE = 6/);
  assert.match(page, /filterChefRequests\(rows, \{query, status, area\}\)/);
  assert.match(page, /filtered\.slice\(safePage \* PAGE_SIZE/);
  assert.match(page, /workingRef\.current === null/);
  assert.match(page, /controller\.abort\(\)/);
  assert.match(page, /<SellerPagination page=\{safePage\} totalPages=\{totalPages\}/);
});

test('menu pagination supports configured page sizes and remembers filter reset', () => {
  const source = read('../src/pages/seller/SellerProductsPage.tsx');
  assert.match(source, /const \[pageSize, setPageSize\] = useState\(6\)/);
  assert.match(source, /size: pageSize/);
  assert.match(source, /setPage\(0\)/);
  assert.match(source, /<SellerPagination page=\{page\}/);
});
