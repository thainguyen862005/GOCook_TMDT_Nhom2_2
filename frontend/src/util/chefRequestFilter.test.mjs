import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// Node 22 can run this typed TS module with --experimental-strip-types.
// When the flag is absent, verify its contract at source level (same as existing project contract tests).
const source = readFileSync(new URL('./chefRequestFilter.ts', import.meta.url), 'utf8');
const hasStripTypes = process.execArgv.some(arg => arg.startsWith('--experimental-strip-types'));
const filter = hasStripTypes ? (await import('./chefRequestFilter.ts')).filterChefRequests : null;
const rows = [
  {id: 1, requestCode:'GC-001',customerName:'Minh',desiredDishes:'Lẩu bò',cuisine:'Việt',address:'Đồng Nai',serviceArea:'75',status:'OPEN',myPrice:undefined},
  {id: 2, requestCode:'GC-002',customerName:'An',desiredDishes:'Món chay',cuisine:'Chay',address:'Hà Nội',serviceArea:'01',status:'OPEN',myPrice:500000},
  {id: 3, requestCode:'GC-003',customerName:'Bình',desiredDishes:'Cơm nhà',cuisine:'Việt',address:'Đồng Nai',serviceArea:'75',status:'CLOSED',myPrice:700000,myOfferStatus:'ACCEPTED'},
  {id: 4, requestCode:'GC-004',customerName:'Lan',desiredDishes:'Hải sản',cuisine:'Việt',address:'TP.HCM',serviceArea:'79',status:'CLOSED',myOfferStatus:'REJECTED'}
];
const settings = (status='all',query='',area='') => ({status,query,area});

test('Chef requests remain scoped to the API and filters preserve records', () => {
  assert.match(source, /rows\.filter\(row\s*=>/);
  assert.match(source, /row\.serviceArea[\s\S]*?\.split\('\|',\s*1\)\[0\]\s*!==\s*filters\.area/);
  if (!filter) return;
  assert.deepEqual(filter(rows,settings()).map(r=>r.id), [1,2,3,4]);
  assert.equal(rows[0].myPrice, undefined);
});
test('filters pending, quoted, accepted and closed without mixing lifecycle states', () => {
  if (!filter) { assert.match(source, /filters\.status === 'accepted'/); return; }
  assert.deepEqual(filter(rows,settings('pending')).map(r=>r.id), [1]);
  assert.deepEqual(filter(rows,settings('quoted')).map(r=>r.id), [2]);
  assert.deepEqual(filter(rows,settings('accepted')).map(r=>r.id), [3]);
  assert.deepEqual(filter(rows,settings('closed')).map(r=>r.id), [3,4]);
});
test('search matches Vietnamese text and area values literally', () => {
  if (!filter) { assert.match(source, /toLocaleLowerCase\('vi-VN'\)/); return; }
  assert.deepEqual(filter(rows,settings('all','lẩu','75')).map(r=>r.id), [1]);
  assert.deepEqual(filter(rows,settings('all','gc-002')).map(r=>r.id), [2]);
  assert.deepEqual(filter(rows,settings('all','An','75')).map(r=>r.id), []);
  assert.deepEqual(filter(rows,settings('all','')).map(r=>r.id), [1,2,3,4]);
});
test('area filter supports verified geo strings and legacy province codes', () => {
  if (!filter) {
    assert.match(source, /\.split\('\|',\s*1\)\[0\]/);
    return;
  }
  const encoded = '75|10.9500|106.8200|15|ward-code|proof';
  const geoRow = {...rows[0], id: 5, serviceArea: encoded};
  const withGeo = [...rows, geoRow];
  assert.deepEqual(filter(withGeo, settings('all', '', '75')).map(r => r.id), [1, 3, 5]);
  assert.deepEqual(filter(withGeo, settings('all', '', '01')).map(r => r.id), [2]);
  assert.deepEqual(filter(withGeo, settings('all', '', '79')).map(r => r.id), [4]);
  assert.equal(geoRow.serviceArea, encoded);
});
test('order search remains bound to chef identity and uses SQL placeholders', () => {
  const root = new URL('../../../src/main/java/com/james/LMS/seller/orders/ChefOrderService.java', import.meta.url);
  // URL above is relative to frontend/src/util; move up to project root.
  const backend = readFileSync(root, 'utf8');
  assert.match(backend, /WHERE o\.chef_id=\? AND o\.is_active=true/);
  assert.match(backend, /POSITION\(LOWER\(\?\)/);
  assert.match(backend, /keyword\.length\(\) > 100/);
  assert.match(backend, /args\.add\(keyword\)/);
});
