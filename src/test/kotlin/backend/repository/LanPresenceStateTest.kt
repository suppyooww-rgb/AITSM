package backend.repository

import backend.models.LanDeviceDto
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

    @Test fun `current view hides abandoned and old offline observations while history retains them`() {
        fun observation(id: String, state: String, ageSeconds: Long) = LanDeviceDto(
            id = id, hostname = id, ipAddress = "192.168.1.${10 + id.length}", reachable = false,
            telemetryAvailable = false, telemetrySourceType = "LAN_OBSERVED",
            lastSeen = now.minusSeconds(ageSeconds).toString(), lanPresenceState = state,
            lastLanSeenAt = now.minusSeconds(ageSeconds).toString(), identityKey = id,
            collectorSiteId = "company-lan"
        )
        val recentOffline = observation("recent", "OFFLINE", 3_600)
        val oldOffline = observation("old", "OFFLINE", 90_000)
        val abandoned = observation("unknown", "UNKNOWN", 60)
        val observations = listOf(recentOffline, oldOffline, abandoned)

        assertEquals(listOf(recentOffline), selectLanObservations(observations, false, now))
        assertEquals(3, selectLanObservations(observations, true, now).size)
    }

    @Test fun `same site and identity collapse to the latest observation`() {
        val older = LanDeviceDto(
            id = "older", hostname = "Device", ipAddress = "192.168.1.20", reachable = false,
            telemetryAvailable = false, telemetrySourceType = "LAN_OBSERVED",
            lastSeen = now.minusSeconds(60).toString(), lastLanSeenAt = now.minusSeconds(60).toString(),
            identityKey = "mac:AABBCCDDEEFF", collectorSiteId = "company-lan"
        )
        val newer = older.copy(id = "newer", lastSeen = now.toString(), lastLanSeenAt = now.toString())
        assertEquals(listOf(newer), selectLanObservations(listOf(older, newer), true, now))
    }

    @Test fun `legacy and stable identities on the same site and address collapse`() {
        val legacy = LanDeviceDto(
            id = "legacy", hostname = "HARUTO", ipAddress = "192.168.0.104", reachable = true,
            telemetryAvailable = true, telemetrySourceType = "COLLECTOR_AGENT",
            lastSeen = now.minusSeconds(5).toString(), lastLanSeenAt = now.minusSeconds(5).toString(),
            identityKey = "collector:legacy:haruto", collectorSiteId = "company-lan"
        )
        val stable = legacy.copy(
            id = "stable", hostname = "Haruto.local", lastSeen = now.toString(), lastLanSeenAt = now.toString(),
            identityKey = "uuid:12345678-1234-1234-1234-123456789012"
        )

        assertEquals(listOf(stable), selectLanObservations(listOf(legacy, stable), true, now))
    }
}
