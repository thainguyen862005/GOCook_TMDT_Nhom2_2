import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const src = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

test('Personalized KNN is visible from home, catalog, but not rendered for Chef/Admin', () => {
  const home = src('../src/pages/home/HomePage.tsx');
  const catalog = src('../src/pages/menu/MenuListPage.tsx');
  const ui = src('../src/components/recommendation/PersonalizedMenuSection.tsx');
  assert.match(home, /<PersonalizedMenuSection\s*\/>/);
  assert.match(catalog, /<PersonalizedMenuSection variant="compact"\s*\/>/);
  assert.match(ui, /if \(loading \|\| isAdmin \|\| isSeller\) return null/);
  assert.match(ui, /Đăng nhập để nhận gợi ý/);
  assert.match(ui, /recommendationClient\.list/);
});

test('Menu detail uses public item-based KNN and no customer history', () => {
  const detail = src('../src/pages/menu/MenuDetailPage.tsx');
  const component = src('../src/components/recommendation/SimilarMenusSection.tsx');
  const api = src('../src/clients/recommendationClient.ts');
  const backend = src('../../src/main/java/com/james/LMS/recommendation/PublicSimilarMenusController.java');
  const service = src('../../src/main/java/com/james/LMS/recommendation/RecommendationService.java');
  const whitelist = src('../../src/main/java/com/james/LMS/config/SecurityConfig.java');
  assert.match(detail, /<SimilarMenusSection menuId=\{data\.id\}\s*\/>/);
  assert.match(component, /recommendationClient\.similar/);
  assert.match(api, /\/api\/public\/menus\/\$\{menuId\}\/similar/);
  assert.match(backend, /@GetMapping\("\/api\/public\/menus\/\{menuId\}\/similar"\)/);
  assert.match(service, /public List<RecommendedMenu> similar/);
  assert.match(whitelist, /"\/api\/public\/menus\/\*\*"/);
  assert.doesNotMatch(backend, /@PreAuthorize/);
});
