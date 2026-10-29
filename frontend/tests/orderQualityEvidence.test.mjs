import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const src = p => readFileSync(new URL(p,import.meta.url),'utf8');

test('Chef transition checks evidence server-side with serialized order lock',()=>{
  const java=src('../../src/main/java/com/james/LMS/seller/orders/ChefOrderService.java');
  assert.match(java,/FOR UPDATE OF o/);
  assert.match(java,/OrderEvidencePolicy\.requireBeforeTransition/);
  assert.match(java,/evidence_type='INGREDIENT_RECEIPT'/);
  assert.match(java,/evidence_type='FINISHED_MEAL'/);
  assert.match(java,/is_active=true/);
});
test('private receipt and meal images have ownership checks and safe storage',()=>{
  const chef=src('../../src/main/java/com/james/LMS/seller/orders/ChefReceiptController.java');
  const customer=src('../../src/main/java/com/james/LMS/seller/orders/CustomerEvidenceController.java');
  assert.match(chef,/lockOrder\(actor\.getId\(\),orderId\)/);
  assert.match(chef,/OrderEvidencePolicy\.validateRemovalStage/);
  assert.match(chef,/OrderEvidencePolicy\.validateUploadStage/);
  assert.match(chef,/chef_receipt_blobs/);
  assert.match(customer,/o.customer_id=\? AND o.is_active=true/);
  assert.match(customer,/CacheControl\.noStore/);
  assert.match(customer,/JOIN chef_receipt_blobs/);
});
test('Customer sees the same evidence as chef; checkout and escrow remain unchanged',()=>{
  const chef=src('../src/pages/seller/SellerOrderDetailPage.tsx');
  const customer=src('../src/pages/order/OrderDetailPage.tsx');
  assert.match(chef,/OrderEvidenceGallery/);
  assert.match(customer,/OrderEvidenceGallery/);
  assert.match(customer,/customerOrderClient\.evidence/);
  assert.match(chef,/missingProof/);
  assert.match(customer,/customerOrderClient\.accept/);
});
test('one additive DB column; existing receipts preserved',()=>{
  const migration=src('../../sql/migrations/20261029_order_quality_evidence.sql');
  assert.match(migration,/ADD COLUMN IF NOT EXISTS evidence_type/);
  assert.match(migration,/DEFAULT 'INGREDIENT_RECEIPT'/);
  assert.doesNotMatch(migration,/(DROP TABLE|DELETE FROM|UPDATE orders|UPDATE order_escrows)/i);
});
test('bell uses a compact preview and retains full history navigation',()=>{
  const bell=src('../src/components/notifications/NotificationBell.tsx');
  assert.match(bell,/notificationClient\.page\(0, 4\)/);
  assert.match(bell,/to="\/notifications"/);
  assert.match(bell,/mark\(item\.id\)/);
});
