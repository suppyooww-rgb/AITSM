const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/frontend/modules/common/js/app.js'), 'utf8');
const ctx = vm.createContext({ Date });
vm.runInContext(source.slice(source.indexOf('function aitsmParseChatTimestamp('), source.indexOf('function normalizeRole(')), ctx);

test('legacy UTC and explicit offsets show the same Philippine 12-hour time', () => {
  for (const timestamp of ['2026-10-29T07:02:00', '2026-10-29T07:02:00Z', '2026-10-29T15:02:00+08:00']) {
    assert.equal(ctx.aitsmFormatChatTime(timestamp), '03:02 PM');
  }
  assert.equal(ctx.aitsmFormatChatTime('2026-10-28T16:00:00Z'), '12:00 AM');
  assert.equal(ctx.aitsmFormatChatTime('2026-10-29T04:00:00Z'), '12:00 PM');
});

test('conversation day uses Philippine midnight instead of browser midnight', () => {
  const now = new Date('2026-10-28T16:30:00Z');
  assert.equal(ctx.aitsmFormatConversationTime('2026-10-28T16:02:00Z', now), '12:02 AM');
  assert.equal(ctx.aitsmFormatConversationTime('2026-10-28T15:59:00Z', now), 'Oct 28');
});

test('missing or invalid times stay blank', () => {
  for (const timestamp of [null, undefined, '', 'invalid']) {
    assert.equal(ctx.aitsmFormatChatTime(timestamp), '');
    assert.equal(ctx.aitsmFormatConversationTime(timestamp), '');
  }
});
