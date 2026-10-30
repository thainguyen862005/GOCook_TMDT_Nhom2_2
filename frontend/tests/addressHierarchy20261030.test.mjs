import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root=new URL('../../',import.meta.url);
const read=p=>readFileSync(new URL(p,root),'utf8');
test('Ward combobox is scoped by selected province and invalidated on province changes',()=>{
  const selector=read('frontend/src/components/location/WardSelect.tsx');
  const chef=read('frontend/src/pages/seller/SellerProfilePage.tsx');
  const customer=read('frontend/src/pages/request/CookingRequestPage.tsx');
  assert.match(selector,/\/api\/public\/wards\?province=/);
  assert.match(selector,/v\.province===province/);
  assert.match(chef,/setProvince\(e\.target\.value\);setWard\(''\);setCoords\(null\)/);
  assert.match(customer,/setProvince\(e\.target\.value\);setWard\(''\);setCoords\(null\)/);
  assert.match(chef,/<WardSelect/);assert.match(customer,/<WardSelect/);
});
test('Neither Customer nor Chef may save unverified arbitrary coordinates',()=>{
  const chef=read('frontend/src/pages/seller/SellerProfilePage.tsx');
  const customer=read('frontend/src/pages/request/CookingRequestPage.tsx');
  const match=read('src/main/java/com/james/LMS/publiccontent/ServiceGeoArea.java');
  const controller=read('src/main/java/com/james/LMS/publiccontent/CookingRequestController.java');
  const workflow=read('src/main/java/com/james/LMS/workflow/ServiceWorkflowController.java');
  assert.match(chef,/!coords\?\.proof/);assert.match(customer,/!coords\?\.proof/);
  assert.match(controller,/verifiedLocations\.confirmCustomer/);
  assert.match(workflow,/verifiedLocations\.confirmChef/);
  assert.match(match,/requestValue\.split/);
});
