import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root=new URL('../../',import.meta.url);
const read=p=>readFileSync(new URL(p,root),'utf8');
test('Chef location and radius are explicitly configured and never silently guessed from province',()=>{
  const ui=read('frontend/src/pages/seller/SellerProfilePage.tsx');
  const backend=read('src/main/java/com/james/LMS/publiccontent/ServiceGeoArea.java');
  assert.match(ui, /<LocationSearch/);assert.match(ui,/RADII.map/);
  assert.match(backend,/Legacy province-only values are intentionally ineligible/);
  assert.match(backend,/distanceKm\(c.center,customer\) <= c.radiusKm/);
});
test('Customer must confirm coordinates and matching rejects too-far and invalid stored zones',()=>{
  const ui=read('frontend/src/pages/request/CookingRequestPage.tsx');
  const service=read('src/main/java/com/james/LMS/publiccontent/CookingRequestController.java');
  assert.match(ui,/!province\|\|!ward\|\|!coords\?\.proof/);assert.match(ui,/<LocationSearch/);
  assert.match(service,/ServiceGeoArea.matches/);
  assert.match(service,/km > c.radiusKm\(\)/);
  assert.match(service,/NOT EXISTS \(SELECT 1 FROM chef_bookings/);
});
test('Public Chef API does not expose exact private starting coordinates',()=>{
  const catalog=read('src/main/java/com/james/LMS/publiccontent/PublicChefService.java');
  const workflow=read('src/main/java/com/james/LMS/workflow/ServiceWorkflowController.java');
  assert.match(catalog,/split_part\(cp.service_area,'\|',1\) AS \\"serviceArea/);
  assert.match(workflow,/split_part\(cp.service_area,'\|',1\) AS "serviceArea"/);
});
test('Manual Admin assignment uses same distance constraint as automatic matching',()=>{
  const workflow=read('src/main/java/com/james/LMS/workflow/ServiceWorkflowController.java');
  assert.match(workflow,/ServiceGeoArea.matches\(\(String\)eligible.get\(0\).get\("chefArea"\),requestArea\)/);
});
