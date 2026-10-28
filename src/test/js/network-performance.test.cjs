const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname,'../../main/resources/static/frontend/modules/common/js/network-performance.js'),'utf8');
const ctx=vm.createContext({window:{},document:{querySelectorAll:()=>[]}});
vm.runInContext(source,ctx);
test('peer does not inherit network speed of observing LAN collector',()=>{
  const results=[{collectorId:'company',status:'COMPLETED',downloadMbps:50}];
  assert.equal(ctx.aitsmNetworkTestForDevice({collectorId:'company'},results),null);
});
test('device speed belongs to its own telemetry collector',()=>{
  const results=[{collectorId:'company',downloadMbps:50},{collectorId:'self',downloadMbps:12}];
  assert.equal(ctx.aitsmNetworkTestForDevice({collectorId:'company',telemetryCollectorId:'self'},results).downloadMbps,12);
});
test('untested device does not fabricate speed or latency',()=>{
  assert.equal(ctx.window.aitsmNetworkSpeedCell({telemetryCollectorId:'self'},'downloadMbps'),'Not tested');
  assert.equal(ctx.window.aitsmNetworkSpeedCell({collectorId:'company'},'latencyMs'),'Requires device collector');
});
