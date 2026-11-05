import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';
import assert from 'node:assert/strict';

const root = process.cwd();
const file = path => readFileSync(join(root, path), 'utf8');

test('chatbot widget is mounted inside public SiteLayout', () => {
  const source = file('src/layouts/SiteLayout.tsx');
  assert.match(source, /import\s*\{\s*ChatbotWidget\s*\}/);
  assert.match(source, /<ChatbotWidget\s*\/>/);
});

test('react chatbot client exposes separate public and customer endpoints', () => {
  const source = file('src/clients/chatbotClient.ts');
  assert.match(source, /\/api\/customer\/chatbot\/message/);
  assert.match(source, /\/api\/public\/chatbot\/message/);
});

test('chatbot shows /help links and renders menu and chef cards', () => {
  const source = file('src/components/chatbot/ChatbotWidget.tsx');
  assert.match(source, /result\?\.menus/);
  assert.match(source, /result\?\.chefs/);
  assert.match(source, /ROUTES\.HELP/);
});


test('public access and Java model are connected without Python runtime', () => {
  const service = file('../src/main/java/com/james/LMS/chatbot/ChatbotService.java');
  const controller = file('../src/main/java/com/james/LMS/chatbot/ChatbotController.java');
  const security = file('../src/main/java/com/james/LMS/config/SecurityConfig.java');
  const publicEndpoints = file('../src/main/java/com/james/LMS/util/PublicEndpointsValidatorUtil.java');
  assert.match(service, /IntentClassifier/);
  assert.match(service, /recommendations\.recommend/);
  assert.match(service, /public_faqs/);
  assert.match(controller, /@PreAuthorize/);
  assert.match(security, /\/api\/public\/chatbot\/message/);
  assert.match(publicEndpoints, /\/api\/public\/chatbot\/message/);
});
