function aitsmNetworkTestForDevice(device, results) {
  // The observing LAN collector does not measure the remote peer's connection.
  return device.telemetryCollectorId ? results.find(t => t.collectorId === device.telemetryCollectorId) : null;
}

(function () {
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
  let tests = [];
  let collectors = [];
  let busy = false;
  const roots = [...document.querySelectorAll('[data-network-performance]')];
  const format = (value, unit) => typeof value === 'number' && Number.isFinite(value) ? `${value.toFixed(2)} ${unit}` : 'Not tested';
  const latest = id => tests.find(t => t.collectorId === id);

  window.aitsmNetworkSpeedCell = function (device, key) {
    // A LAN observer is not necessarily the device's own collector.
    const id = device.telemetryCollectorId;
    const test = aitsmNetworkTestForDevice(device, tests);
    if (!id) return 'Requires device collector';
    if (!test) return 'Not tested';
    if (test.status !== 'COMPLETED') return escape(test.status.replaceAll('_', ' '));
    const unit = key === 'latencyMs' ? 'ms' : 'Mbps';
    return `<span title='${escape(`Tested ${new Date(test.completedAt).toLocaleString()} · connection to AITSM server`)}'>${format(test[key], unit)}</span>`;
  };

  function build(root) {
    root.innerHTML = `<div class='small'>Measured connection from each collector PC to the AITSM server. Download/upload: Mbps; latency: HTTPS round-trip ms. A 1 MiB sample in each direction gives a connection estimate, not an ISP maximum-speed certification.</div>
      <div class='card-head' style='margin-top:12px'><label>Collector <select class='form-control' data-network-collector aria-label='Collector to test'></select></label><button class='btn btn-primary' type='button' data-network-run>Run speed test</button></div>
      <p class='small' data-network-message role='status' aria-live='polite'>On demand only. Each test transfers about 2 MiB. Collector 1.4.0 or newer is required; wait five minutes between tests.</p>
      <div class='table-wrap'><table><thead><tr><th>Collector</th><th>Download</th><th>Upload</th><th>Latency</th><th>Last tested</th><th>Status</th></tr></thead><tbody data-network-results></tbody></table></div>`;
    root.querySelector('[data-network-run]').addEventListener('click', async () => {
      const selected = root.querySelector('[data-network-collector]').value;
      if (!selected || busy) return;
      busy = true;
      const button = root.querySelector('[data-network-run]');
      button.disabled = true;
      try {
        await window.aitsmFetchJson(`/api/monitoring/network-tests/${encodeURIComponent(selected)}`, {method:'POST'});
        root.querySelector('[data-network-message]').textContent = 'Test queued. The selected collector will pick it up within about 15 seconds if online.';
        await refresh();
      } catch(error) { root.querySelector('[data-network-message]').textContent = error.message || 'Unable to queue network test.'; }
      finally { busy = false; button.disabled = false; }
    });
  }

  function render(root) {
    const select = root.querySelector('[data-network-collector]');
    const selected = select.value;
    select.innerHTML = collectors.map(c => `<option value='${escape(c.collectorId)}'>${escape(c.name)} · ${escape(c.scope)} · ${escape(c.agentVersion || 'unknown version')}</option>`).join('');
    if (collectors.some(c => c.collectorId === selected)) select.value = selected;
    root.querySelector('[data-network-results]').innerHTML = collectors.length ? collectors.map(c => {
      const test = latest(c.collectorId);
      const complete = test?.status === 'COMPLETED';
      return `<tr><td>${escape(c.name)}</td><td>${complete ? format(test.downloadMbps,'Mbps') : '—'}</td><td>${complete ? format(test.uploadMbps,'Mbps') : '—'}</td><td>${complete ? format(test.latencyMs,'ms') : '—'}</td><td>${test?.completedAt ? escape(new Date(test.completedAt).toLocaleString()) : 'Not tested'}</td><td>${escape(test?.error || test?.status || 'NOT TESTED')}</td></tr>`;
    }).join('') : '<tr><td colspan="6">No active collectors. Enroll a collector to measure its connection.</td></tr>';
  }

  async function refresh() {
    try {
      const [newTests, newCollectors] = await Promise.all([window.aitsmFetchJson('/api/monitoring/network-tests'), window.aitsmFetchJson('/api/monitoring/collectors')]);
      tests = Array.isArray(newTests) ? newTests : [];
      collectors = (Array.isArray(newCollectors) ? newCollectors : []).filter(c => !['REVOKED','ARCHIVED'].includes(c.state));
      roots.forEach(render);
      window.dispatchEvent(new CustomEvent('aitsm:network-results'));
    } catch(error) { roots.forEach(root => { root.querySelector('[data-network-message]').textContent = error.message || 'Network results could not be loaded.'; }); }
  }
  roots.forEach(build);
  if (roots.length) {
    refresh();
    window.setInterval(() => { if (!document.hidden) refresh(); }, 10000);
  }
})();
