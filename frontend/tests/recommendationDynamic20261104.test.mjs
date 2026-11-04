import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const read = (file) => readFileSync(new URL(file, import.meta.url), 'utf8');

test('new recommendation API is private and no checkout writes', () => {
  const controller = read('../../src/main/java/com/james/LMS/recommendation/RecommendationController.java');
  const service = read('../../src/main/java/com/james/LMS/recommendation/RecommendationService.java');
  const sec = read('../../src/main/java/com/james/LMS/config/SecurityConfig.java');
  assert.match(controller, /ROLE_USER/);
  assert.match(controller, /ROLE_INSTRUCTOR/);
  assert.match(service, /e.status='RELEASED'/);
  assert.match(service, /o.status='COMPLETED'/);
  assert.doesNotMatch(sec, /"\/api\/customer\/recommendations"/);
  assert.doesNotMatch(service, /UPDATE orders|INSERT INTO order_escrows/);
});

test('index uses fixed hashing, background sync and immutable snapshots', () => {
  const encoder = read('../../src/main/java/com/james/LMS/recommendation/MenuFeatureEncoder.java');
  const index = read('../../src/main/java/com/james/LMS/recommendation/KnnIndex.java');
  const sync = read('../../src/main/java/com/james/LMS/recommendation/RecommendationIndexSync.java');
  assert.match(encoder, /CRC32/);
  assert.match(index, /upsert\(mutable, existingId, newId/);
  assert.match(sync, /@Scheduled/);
  assert.match(sync, /volatile KnnIndex\.Snapshot snapshot/);
});

test('home contains isolated KNN cards and still shows featured menus', () => {
  const home = read('../src/pages/home/HomePage.tsx');
  const ui = read('../src/components/recommendation/PersonalizedMenuSection.tsx');
  assert.match(home, /<PersonalizedMenuSection\s*\/>/);
  assert.match(home, /menuClient\.featured/);
  assert.match(ui, /matchPercent/);
  assert.match(ui, /isAdmin, isSeller/);
});
