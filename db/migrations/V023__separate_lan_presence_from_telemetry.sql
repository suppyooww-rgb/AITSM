CREATE TABLE IF NOT EXISTS monitoring_lan_observations (
  id BIGSERIAL PRIMARY KEY,
  collector_id UUID NOT NULL REFERENCES monitoring_collectors(id) ON DELETE CASCADE,
  identity_key VARCHAR(220) NOT NULL,
  device_key VARCHAR(200) NOT NULL,
  hostname VARCHAR(200) NOT NULL,
  ip_address VARCHAR(45) NOT NULL,
  mac_address VARCHAR(32),
  device_uuid VARCHAR(160),
  os_details VARCHAR(300),
  telemetry_available BOOLEAN NOT NULL DEFAULT FALSE,
  cpu_usage_percent DECIMAL(5,2),
  memory_usage_percent DECIMAL(5,2),
  storage_total_bytes BIGINT,
  storage_used_bytes BIGINT,
  measurement_at TIMESTAMP,
  first_seen_at TIMESTAMP NOT NULL,
  last_seen_at TIMESTAMP NOT NULL,
  UNIQUE(collector_id, identity_key)
);

CREATE INDEX IF NOT EXISTS monitoring_lan_observations_collector_idx
  ON monitoring_lan_observations(collector_id);
CREATE INDEX IF NOT EXISTS monitoring_lan_observations_ip_idx
  ON monitoring_lan_observations(ip_address);
CREATE INDEX IF NOT EXISTS monitoring_lan_observations_last_seen_idx
  ON monitoring_lan_observations(last_seen_at);

INSERT INTO monitoring_lan_observations (
  collector_id, identity_key, device_key, hostname, ip_address, mac_address,
  device_uuid, os_details, telemetry_available, cpu_usage_percent,
  memory_usage_percent, storage_total_bytes, storage_used_bytes,
  measurement_at, first_seen_at, last_seen_at
)
SELECT device.collector_id,
       COALESCE(device.identity_key, 'collector:' || device.collector_id::text || ':' || lower(device.device_key)),
       device.device_key, device.hostname, device.ip_address, device.mac_address,
       device.device_uuid, device.os_details, device.telemetry_available,
       device.cpu_usage_percent, device.memory_usage_percent,
       device.storage_total_bytes, device.storage_used_bytes,
       device.measurement_at, device.received_at, device.last_seen_at
FROM monitoring_devices device
JOIN monitoring_collectors collector ON collector.id = device.collector_id
WHERE collector.scope = 'LAN'
ON CONFLICT (collector_id, identity_key) DO UPDATE
SET device_key = EXCLUDED.device_key,
    hostname = EXCLUDED.hostname,
    ip_address = EXCLUDED.ip_address,
    mac_address = EXCLUDED.mac_address,
    device_uuid = EXCLUDED.device_uuid,
    os_details = EXCLUDED.os_details,
    telemetry_available = EXCLUDED.telemetry_available,
    cpu_usage_percent = EXCLUDED.cpu_usage_percent,
    memory_usage_percent = EXCLUDED.memory_usage_percent,
    storage_total_bytes = EXCLUDED.storage_total_bytes,
    storage_used_bytes = EXCLUDED.storage_used_bytes,
    measurement_at = EXCLUDED.measurement_at,
    last_seen_at = GREATEST(monitoring_lan_observations.last_seen_at, EXCLUDED.last_seen_at);
