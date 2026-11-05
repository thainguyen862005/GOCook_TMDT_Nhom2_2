import { readFileSync } from 'node:fs';
import test from 'node:test';
import assert from 'node:assert/strict';

const read = path => readFileSync(path, 'utf8');
test('one floating launcher, inbox stays in header', () => {
  const layout = read('src/layouts/SiteLayout.tsx');
  const widget = read('src/components/chatbot/ChatbotWidget.tsx');
  assert.doesNotMatch(layout, /className="help-float"/);
  assert.match(layout, /header-messages-link/);
  assert.match(layout, /<ChatbotWidget \/>/);
  assert.match(widget, /gocook-chatbot-launcher/);
  assert.match(widget, /aria-label=\{open \?/);
});

test('follow-up price constraints have Java and TS support', () => {
  const slots = read('../src/main/java/com/james/LMS/chatbot/ChatSlots.java');
  const service = read('../src/main/java/com/james/LMS/chatbot/ChatbotService.java');
  const types = read('src/clients/chatbotClient.ts');
  assert.match(slots, /minUnitPrice/);
  assert.match(service, /:minUnitPrice/);
  assert.match(types, /minUnitPrice/);
});
