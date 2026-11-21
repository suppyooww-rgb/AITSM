package backend.services

import backend.models.LanDeviceDto
import kotlin.test.Test
import kotlin.test.assertEquals

class ManagedLanPresenceTest {
    private val ranges = listOf("10.38.201.0/24")
    private val pc = LanDeviceDto(
        id = "pc", hostname = "pc", ipAddress = "192.168.1.6", reachable = false,
        telemetryAvailable = true, telemetrySourceType = "COLLECTOR_AGENT", lastSeen = "",
        registeredCollectorId = "self", lanPresenceState = "UNKNOWN"
    )

    @Test fun presenceTransitions() {
        assertEquals("ON_LAN", managedLanPresence(pc.copy(lanPresenceState = "ON_LAN"), ranges))
        assertEquals("OFF_LAN", managedLanPresence(pc, ranges))
        assertEquals("STALE", managedLanPresence(pc.copy(telemetryState = "STALE"), ranges))
        assertEquals("OFFLINE", managedLanPresence(pc.copy(telemetryState = "OFFLINE"), ranges))
        assertEquals("UNKNOWN", managedLanPresence(pc, emptyList()))
        assertEquals("UNKNOWN", managedLanPresence(pc.copy(ipAddress = "10.38.201.42"), ranges))
    }
}
