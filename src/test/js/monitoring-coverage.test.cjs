const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/frontend/monitoring/js/monitoring.js'), 'utf8');
const hosts = { monitorSummary: {}, monitorHealthPanel: {} };
const ctx = vm.createContext({ Number, Math, document: { getElementById: id => hosts[id] },
  aitsmEscapeHtml: value => String(value).replaceAll('<', '&lt;'), formatLastSeen: () => 'Not observed' });
vm.runInContext(source.slice(source.indexOf('function monitoringCoverage('), source.indexOf('function renderMonitorCards(')), ctx);

test('LAN summary and health panel use the same on-LAN denominator with remote registrations', () => {
  const summary = { onLanDevices: 2, telemetryAvailableDevices: 1 };
  ctx.renderMonitorSummary(summary);
  ctx.renderMonitoringHealthPanel(summary, [{ lanPresenceState: 'UNKNOWN', telemetryState: 'AVAILABLE' }]);
  assert.equal(ctx.monitoringCoverage(summary), 50);
  assert(hosts.monitorSummary.innerHTML.includes('50%'));
  assert(hosts.monitorHealthPanel.innerHTML.includes('50%'));
});

test('no confirmed LAN devices gives unavailable and scan errors are escaped', () => {
  const summary = { onLanDevices: 0, telemetryAvailableDevices: 0, discoveryError: '<script>bad</script>' };
  ctx.renderMonitorSummary(summary);
  ctx.renderMonitoringHealthPanel(summary, []);
  assert.equal(ctx.monitoringCoverage(summary), null);
  assert(hosts.monitorSummary.innerHTML.includes('Unavailable'));
  assert(hosts.monitorHealthPanel.innerHTML.includes('Unavailable'));
  assert(!hosts.monitorHealthPanel.innerHTML.includes('<script>'));
});
