DELETE FROM monitoring_lan_observations
WHERE last_seen_at < CURRENT_TIMESTAMP - INTERVAL '30 days';
