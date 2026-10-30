import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const root = new URL('../../', import.meta.url);
const read = path => readFileSync(new URL(path,root),'utf8');

test('Customer and Chef select official province codes instead of free text',()=>{
  const customer=read('frontend/src/pages/request/CookingRequestPage.tsx');
  const chef=read('frontend/src/pages/seller/SellerProfilePage.tsx');
  const menu=read('frontend/src/pages/menu/MenuDetailPage.tsx');
  const matcher=read('src/main/java/com/james/LMS/publiccontent/CookingRequestController.java');
  assert.match(customer, /<select name="serviceArea" required/);
  assert.match(chef, /PROVINCES.map/);
  assert.match(menu, /Chọn tỉnh\/thành/);
  assert.match(matcher, /ServiceGeoArea.customer/);
  assert.match(matcher, /ServiceGeoArea.distanceKm/);
  assert.doesNotMatch(matcher, /cp\.service_area[^\n]*ILIKE/);
});

test('Verified reviews are tied to completed and settled orders',()=>{
  const reviews=read('src/main/java/com/james/LMS/publiccontent/VerifiedReviewController.java');
  const ddl=read('sql/migrations/20261030_verified_order_reviews.sql');
  assert.match(reviews,/o.status='COMPLETED' AND e.status='RELEASED'/);
  assert.match(reviews,/ON CONFLICT \(order_id\) DO NOTHING/);
  assert.match(ddl,/order_id BIGINT NOT NULL UNIQUE/);
});

test('Automatic refund queues but does not simulate settled money',()=>{
  const refunds=read('src/main/java/com/james/LMS/refund/RefundService.java');
  const refundDriver=read('src/main/java/com/james/LMS/refund/SimulatedRefundGateway.java');
  const chef=read('src/main/java/com/james/LMS/seller/orders/ChefOrderService.java');
  assert.match(refunds,/public Optional<Refund> queueRefund/);
  assert.match(chef,/refunds.queueRefund/);
  assert.match(refundDriver,/ConditionalOnProperty/);
  assert.match(read('src/main/java/com/james/LMS/refund/UnavailableRefundGateway.java'),/RefundOutcome.failure/);
});
