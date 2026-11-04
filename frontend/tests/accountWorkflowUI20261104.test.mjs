import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = file => readFileSync(new URL(`../src/${file}`, import.meta.url), 'utf8');

test('request list and inbox both use the same account sidebar as order history', () => {
  const requests = read('pages/request/MyRequestsPage.tsx');
  const inbox = read('pages/support/MessagesPage.tsx');
  const orders = read('pages/order/OrdersPage.tsx');
  for (const page of [requests, inbox, orders]) {
    assert.match(page, /account-workspace/);
    assert.match(page, /<AccountSidebar /);
  }
  assert.match(requests, /active="chat"/);
  assert.match(inbox, /active="messages"/);
});

test('request list formats Unix timestamps instead of printing raw milliseconds', () => {
  const list = read('pages/request/MyRequestsPage.tsx');
  const detail = read('pages/request/RequestDetailPage.tsx');
  const util = read('util/format.ts');
  assert.match(list, /requestDate\(row\.mealDate\)/);
  assert.match(detail, /requestDate\(data\.mealDate\)/);
  assert.match(util, /\/\^\\d\{10,13\}\$\//);
});

test('personalized recommendation filters purchased neighbors before the K cutoff', () => {
  const java = readFileSync(new URL('../../src/main/java/com/james/LMS/recommendation/RecommendationService.java', import.meta.url), 'utf8');
  assert.match(java, /if \(accepted >= k\) break/);
  assert.match(java, /if \(purchased\.contains\(neighbor\.id\(\)\)/);
  assert.match(java, /accepted\+\+/);
});
