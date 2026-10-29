import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

test('general cooking requests may omit preferredChefId without a primitive Long unbox', () => {
  const java = source('../../src/main/java/com/james/LMS/publiccontent/CookingRequestController.java');
  assert.match(java, /final Long targetChef = resolveChefId\(data\.preferredChefId\(\), chosenMenu\)/);
  assert.match(java, /static Long resolveChefId\(Long preferredChefId, Map<String,Object> chosenMenu\)/);
  assert.match(java, /return preferredChefId;/);
  assert.doesNotMatch(java, /chosenMenu==null\?data\.preferredChefId\(\):/);
});

test('account workspace contains sidebar and account-content, not three top-level children', () => {
  const page = source('../src/pages/account/AccountPage.tsx');
  assert.match(page, /<AccountSidebar active="account" \/>/);
  assert.match(page, /<div className="account-content">/);
  assert.match(page, /<div className="account-security" id="account-security">/);
  const css = source('../src/styles/globals.css');
  assert.match(css, /\.account-content \{/);
  assert.match(css, /\.account-security \{/);
});

test('profile form sends existing dietary and allergy information through user API', () => {
  const page = source('../src/pages/account/AccountPage.tsx');
  const client = source('../src/clients/userClient.ts');
  assert.match(page, /dietaryPreferences\.trim\(\), allergyInfo\.trim\(\)/);
  assert.match(client, /JSON\.stringify\(\{ username, phone, defaultAddress, dietaryPreferences, allergyInfo \}\)/);
});

test('profile sidebar uses security section instead of forgot-password flow', () => {
  const sidebar = source('../src/components/account/AccountSidebar.tsx');
  assert.match(sidebar, /\$\{ROUTES\.ACCOUNT\}#account-security/);
  assert.doesNotMatch(sidebar, /ROUTES\.FORGOT_PASSWORD/);
});
