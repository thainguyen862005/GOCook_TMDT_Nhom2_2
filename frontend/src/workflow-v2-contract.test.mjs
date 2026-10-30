import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const source=(name)=>readFileSync(new URL(name,import.meta.url),'utf8');
test('existing menu payment is distinct from other Chef quote',()=>{
 const menu=source('./pages/menu/MenuDetailPage.tsx');
 assert.match(menu,/bookingMode==='creator'/);
 assert.match(menu,/bookingMode==='choose'/);
 assert.match(menu,/bookingMode==='match'/);
 assert.match(menu,/ROUTES.CHECKOUT/);
 assert.match(menu,/ROUTES.REQUEST/);
});
test('request submits Chef preference and verified service area',()=>{
 const form=source('./pages/request/CookingRequestPage.tsx');
 assert.match(form,/preferredChefId:chefId/);
 assert.match(form,/preferredMenuId:menuId/);
 assert.match(form,/serviceArea:\s*formatPoint\(province,\s*coords\)/);
 assert.match(form,/coords\?\.proof/);
 assert.match(form,/coords\.wardCode\s*!==\s*ward/);
 assert.match(form,/address:\s*coords\.label\.trim\(\)/);
});
test('one real inbox endpoint serves both roles',()=>{
 assert.match(source('./clients/workflowClient.ts'),/api\/workflow\/threads/);
 assert.match(source('./pages/support/MessagesPage.tsx'),/workflowClient.threads\(\)/);
});
test('Chef queue links to offer and chat screens',()=>{
 const queue=source('./pages/seller/SellerRequestsPage.tsx');
 assert.match(queue,/workflowClient.chefRequests/);
 assert.match(queue,/workflowClient.offer/);
 assert.match(queue,/ROUTES.CHAT/);
});
