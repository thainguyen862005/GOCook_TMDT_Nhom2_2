import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

// Exercise the actual TypeScript redirect helper without a running browser/server.
const require = createRequire(import.meta.url);
const ts = require('typescript');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = readFileSync(path.join(root, 'src/hooks/checkout/orderPayment.ts'), 'utf8');
const transpiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;

function load(checkoutResponse) {
  const urls = [];
  const calls = [];
  const exports = {};
  const sandbox = {
    exports, URL, Error,
    crypto: { randomUUID: () => '123e4567-e89b-12d3-a456-426614174000' },
    window: { location: { assign: value => urls.push(value) } },
    require: () => ({ paymentClient: { checkoutOrder: async request => {
      calls.push(request);
      return checkoutResponse;
    } } }),
  };
  vm.runInNewContext(transpiled, sandbox);
  return { helper: exports, urls, calls };
}

const valid = { orderCode: 'GC261029CHDYKX', payUrl: 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=GC261029CHDYKX&vnp_Amount=77200000&vnp_SecureHash=abc' };

test('valid sandbox URL redirects once without changing amount or signature', async () => {
  const { helper, urls, calls } = load(valid);
  await helper.beginOrderPayment(valid.orderCode);
  assert.equal(urls.length, 1);
  const url = new URL(urls[0]);
  assert.equal(url.host, 'sandbox.vnpayment.vn');
  assert.equal(url.searchParams.get('vnp_Amount'), '77200000');
  assert.equal(url.searchParams.get('vnp_SecureHash'), 'abc');
  assert.equal(calls[0].orderCode, valid.orderCode);
  assert.equal(calls[0].idempotencyKey, '123e4567-e89b-12d3-a456-426614174000');
});

test('HTTPS host must be actual VNPay, not lookalike or HTTP', () => {
  for (const payUrl of [
    'https://sandbox.vnpayment.vn.evil.test/pay?vnp_SecureHash=abc',
    'http://sandbox.vnpayment.vn/pay?vnp_SecureHash=abc',
    'javascript:alert(1)',
    'https://example.org/pay?vnp_SecureHash=abc',
  ]) {
    const { helper, urls } = load(valid);
    assert.throws(() => helper.redirectToVnpay({ ...valid, payUrl }, valid.orderCode));
    assert.equal(urls.length, 0);
  }
});

test('refuse missing HMAC, mismatched order, and empty URL', () => {
  const { helper, urls } = load(valid);
  assert.throws(() => helper.redirectToVnpay({ ...valid, payUrl: 'https://sandbox.vnpayment.vn/pay' }, valid.orderCode));
  assert.throws(() => helper.redirectToVnpay({ ...valid, orderCode: 'OTHER' }, valid.orderCode));
  assert.throws(() => helper.redirectToVnpay({ ...valid, payUrl: '' }, valid.orderCode));
  assert.equal(urls.length, 0);
});

test('reject malformed order codes without creating payment attempts', async () => {
  const { helper, calls, urls } = load(valid);
  await assert.rejects(helper.beginOrderPayment('..'));
  await assert.rejects(helper.beginOrderPayment('GC261029;DROP TABLE'));
  assert.equal(calls.length, 0);
  assert.equal(urls.length, 0);
});
