package backend.repository

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class LanPresenceStateTest {
    private val now = Instant.parse("2026-10-23T00:00:00Z")

    @Test fun `recent lan observation is on lan`() {
        assertEquals("ON_LAN", deriveLanPresenceState(true, now.minusSeconds(60), "AVAILABLE", now))
    }

    @Test fun `fresh remote telemetry marks a missing lan device off lan`() {
        assertEquals("OFF_LAN", deriveLanPresenceState(true, now.minusSeconds(180), "AVAILABLE", now))
    }

    @Test fun `missing device becomes stale then offline`() {
        assertEquals("STALE", deriveLanPresenceState(true, now.minusSeconds(180), "UNAVAILABLE", now))
        assertEquals("OFFLINE", deriveLanPresenceState(true, now.minusSeconds(601), "UNAVAILABLE", now))
    }

    @Test fun `device presence is unknown when lan collector is not current`() {
        assertEquals("UNKNOWN", deriveLanPresenceState(false, now.minusSeconds(30), "AVAILABLE", now))
    }

    @Test fun `telemetry freshness uses the shared two and ten minute boundaries`() {
        assertEquals("AVAILABLE", deriveTelemetryState(now.minusSeconds(60), now))
        assertEquals("STALE", deriveTelemetryState(now.minusSeconds(180), now))
        assertEquals("OFFLINE", deriveTelemetryState(now.minusSeconds(601), now))
        assertEquals("UNAVAILABLE", deriveTelemetryState(null, now))
    }
}
