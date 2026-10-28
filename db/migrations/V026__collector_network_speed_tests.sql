CREATE TABLE IF NOT EXISTS collector_network_tests (
    id UUID PRIMARY KEY,
    collector_id UUID NOT NULL REFERENCES monitoring_collectors(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    probe_count INTEGER NOT NULL DEFAULT 0,
    download_mbps DOUBLE PRECISION,
    upload_mbps DOUBLE PRECISION,
    latency_ms DOUBLE PRECISION,
    error VARCHAR(300)
);
CREATE INDEX IF NOT EXISTS collector_network_tests_latest_idx ON collector_network_tests(collector_id, requested_at DESC);
