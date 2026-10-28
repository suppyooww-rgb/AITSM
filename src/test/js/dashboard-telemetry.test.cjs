const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/frontend/dashboard-admin/js/dashboard.js'), 'utf8');
const start = source.indexOf('function averageLiveTelemetry(');
const end = source.indexOf('function renderSystemPerformance()', start);
const ctx = vm.createContext({ Date, Number });
vm.runInContext(source.slice(start, end), ctx);
const now = Date.parse('2026-10-28T16:00:00Z');
const device = { telemetryAvailable: true, stale: false, cpuUsagePercent: 17, memoryUsagePercent: 64, receivedAt: '2026-10-28T15:59:30Z' };
test('agent telemetry works without a LAN discovery observation', () => {
  assert.equal(ctx.averageLiveTelemetry([device], 'cpuUsagePercent', now), 17);
  assert.equal(ctx.averageLiveTelemetry([device], 'memoryUsagePercent', now), 64);
});
test('missing and old readings are unavailable and do not dilute average', () => {
  assert.equal(ctx.averageLiveTelemetry([], 'cpuUsagePercent', now), null);
  assert.equal(ctx.averageLiveTelemetry([ {...device, receivedAt:'2026-10-27T15:59:30Z'} ], 'cpuUsagePercent', now), null);
  assert.equal(ctx.averageLiveTelemetry([device, {...device,cpuUsagePercent:null}], 'cpuUsagePercent', now), 17);
  assert.equal(ctx.averageLiveTelemetry([ {...device,cpuUsagePercent:0} ], 'cpuUsagePercent', now), 0);
});
