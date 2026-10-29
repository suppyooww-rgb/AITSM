package backend.services

import backend.models.CollectorDeviceReport
import backend.models.CollectorInventoryReport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnHostTelemetryTest {
    private val approved = listOf("10.38.201.0/24")
    private val host = CollectorDeviceReport(
        deviceKey = "host:elle", hostname = "ELLE", ipAddress = "192.168.0.100",
        reachable = true, telemetryAvailable = true, inventory = CollectorInventoryReport(fullDeviceName = "ELLE")
    )

    @Test fun `company host reports private telemetry outside discovery prefix`() {
        assertTrue(acceptsCollectorReading(host, approved))
        assertFalse(isUsableLanHost(host.ipAddress, approved))
    }

    @Test fun `peers outside approved range are still rejected`() {
        assertFalse(acceptsCollectorReading(host.copy(deviceKey = "peer:192.168.0.100", telemetryAvailable = false, inventory = null), approved))
        assertFalse(acceptsCollectorReading(host.copy(inventory = null), approved))
    }

    @Test fun `public and broadcast addresses are never host exceptions`() {
        assertFalse(acceptsCollectorReading(host.copy(ipAddress = "8.8.8.8"), approved))
        assertFalse(acceptsCollectorReading(host.copy(ipAddress = "192.168.255.255"), approved))
    }
}
