import {readFileSync} from 'node:fs';
import {join} from 'node:path';
import {test} from 'node:test';
import assert from 'node:assert/strict';
const root=process.cwd();
const read=p=>readFileSync(join(root,p),'utf8');
test('chef orders UI no longer uses hardcoded SellerDemoContext orders',()=>{
 for(const page of ['SellerOrdersPage','SellerOrderDetailPage','SellerDashboardPage']){
  const text=read(`src/pages/seller/${page}.tsx`);
  assert.doesNotMatch(text,/useSellerDemo|sellerMockData|initialChefOrders/);
  assert.match(text,/chefOrderClient/);
 }
});
test('backend commands use principal, version and escrow checks',()=>{
 const service=read('../src/main/java/com/james/LMS/seller/orders/ChefOrderService.java');
 assert.match(service,/chef_id=\?/);
 assert.match(service,/version=version\+1/);
 assert.match(service,/"HELD"\.equals/);
 assert.match(service,/FOR UPDATE OF u/);
 assert.match(service,/chef_bookings/);
 assert.doesNotMatch(service,/UPDATE order_escrows SET status='RELEASED'/);
});
test('customer acceptance uses existing escrow protection',()=>{
 const controller=read('../src/main/java/com/james/LMS/seller/orders/CustomerAcceptanceController.java');
 assert.match(controller,/escrows\.release\(id,actor\.getId\(\)\)/);
});
test('receipt endpoints require chef authorization and no public upload',()=>{
 const source=read('../src/main/java/com/james/LMS/seller/orders/ChefReceiptController.java');
 assert.match(source,/ROLE_INSTRUCTOR/);
 assert.match(source,/chef_id=\?/);
 assert.match(source,/CacheControl\.noStore/);
});
