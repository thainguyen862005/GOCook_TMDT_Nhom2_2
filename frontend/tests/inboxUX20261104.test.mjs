import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const source = readFileSync(fileURLToPath(new URL('../src/util/messageThreads.ts', import.meta.url)), 'utf8');
const rendered = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
const { normalizeThreadQuery, sortAndFilterThreads, conversationPath } = await import(`data:text/javascript;base64,${Buffer.from(rendered.outputText).toString('base64')}`);
const makeThread = (kind, id, peerId, peerName, title, date) => ({ kind, scopeId: id, peerId, peerName, title, lastMessageAt: date });
const data = [
  makeThread('REQUEST', 123, 11, 'Bếp Bùi Giang Tú', 'REQ-123', '2026-10-30T09:00:00+07:00'),
  makeThread('ORDER', 2, 5, 'Bếp Hồng', 'GC-567', '2026-11-01T13:05:00+07:00'),
  makeThread('REQUEST', 99, 7, 'Bếp Hoa', 'REQ-990', null),
];

test('inbox search ignores accents and preserves the original records', () => {
  assert.equal(normalizeThreadQuery('  ĐẦU BẾP  '), 'dau bep');
  const filtered = sortAndFilterThreads(data, 'BUi giang tu', 'all');
  assert.equal(filtered.length, 1);
  assert.equal(filtered[0].scopeId, 123);
  assert.equal(data[0].peerName, 'Bếp Bùi Giang Tú');
});

test('inbox filters request vs. order and sorts by latest activity', () => {
  assert.deepEqual(sortAndFilterThreads(data, '', 'all').map(t => t.scopeId), [2, 123, 99]);
  assert.deepEqual(sortAndFilterThreads(data, '', 'REQUEST').map(t => t.scopeId), [123, 99]);
  assert.deepEqual(sortAndFilterThreads(data, 'GC-567', 'ORDER').map(t => t.scopeId), [2]);
  assert.deepEqual(sortAndFilterThreads(data, 'does not match', 'all'), []);
});

test('inbox links retain backend-scoped IDs; order and request have distinct params', () => {
  assert.equal(conversationPath(data[0], '/chat'), '/chat?requestId=123&peerId=11');
  assert.equal(conversationPath(data[1], '/chat'), '/chat?orderId=2');
});

test('inbox is a single navigable list with accessible filtering and states', () => {
  const jsx = readFileSync(fileURLToPath(new URL('../src/pages/support/MessagesPage.tsx', import.meta.url)), 'utf8');
  const css = readFileSync(fileURLToPath(new URL('../src/styles/messages.css', import.meta.url)), 'utf8');
  assert.match(jsx, /messages-list/);
  assert.match(jsx, /aria-pressed=/);
  assert.match(jsx, /aria-label=\{`Mở hội thoại với/);
  assert.match(jsx, /role="alert"/);
  assert.match(jsx, /role="status"/);
  assert.match(jsx, /workflowClient\.threads\(\)/);
  assert.match(css, /@media \(max-width: 600px\)/);
  assert.match(css, /\.messages-thread:focus-visible/);
});
