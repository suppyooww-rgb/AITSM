package backend.repository

import backend.config.*
import backend.models.*
import java.sql.DriverManager
import java.time.*
import java.util.UUID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import kotlin.test.*

class RegisteredCollectorMonitoringTest {
    private val now = Instant.parse("2026-10-27T04:00:00Z")
    private val registered = LanDeviceDto(
        id = "registered-1", hostname = "TEST-PC", ipAddress = "10.38.201.20",
        reachable = false, telemetryAvailable = true, telemetrySourceType = "COLLECTOR_AGENT",
        lastSeen = now.toString(), lastTelemetryAt = now.toString(), lastLanSeenAt = null,
        lanPresenceState = "UNKNOWN", identityKey = "uuid:11111111-2222-4333-8444-555555555555",
        deviceUuid = "11111111-2222-4333-8444-555555555555", macAddress = "AA-BB-CC-DD-EE-01",
        registeredCollectorId = "self", ownerUserId = 7, ownerName = "Test Owner"
    )
    private fun merge(observations: List<LanDeviceDto>, devices: List<LanDeviceDto> = listOf(registered)) =
        mergeRegisteredLanDevices(observations, devices, false, now)

    @Test fun `managed device is visible without a LAN scan and does not claim LAN presence`() {
        assertEquals(registered, merge(emptyList()).single())
        assertNull(merge(emptyList()).single().lastLanSeenAt)
        assertFalse(merge(emptyList()).single().reachable)
    }

    @Test fun `MAC observation enriches registered device without duplicate or ownership loss`() {
        val observation = registered.copy(id = "peer", deviceUuid = null, identityKey = "mac:AABBCCDDEE01",
            macAddress = "aa:bb:cc:dd:ee:01", telemetryAvailable = false, telemetrySourceType = "LAN_OBSERVED",
            reachable = true, lanPresenceState = "ON_LAN", lastLanSeenAt = now.toString())
        val result = merge(listOf(observation)).single()
        assertEquals(registered.id, result.id)
        assertEquals("Test Owner", result.ownerName)
        assertEquals("ON_LAN", result.lanPresenceState)
        assertTrue(result.telemetryAvailable)
    }

    @Test fun `routed peers cannot join a managed device merely by private IP and hostname`() {
        val observation = registered.copy(id = "routed-peer", deviceUuid = null, identityKey = "collector:lan:peer",
            macAddress = null, lanPresenceState = "ON_LAN", lastLanSeenAt = now.toString())
        val results = merge(listOf(observation))
        assertEquals(2, results.size)
        assertEquals("UNKNOWN", results.single { it.id == registered.id }.lanPresenceState)
    }

    @Test fun `conflicting UUIDs cannot join through a reused MAC`() {
        val observation = registered.copy(id = "other-hardware",
            deviceUuid = "22222222-2222-4333-8444-555555555555",
            identityKey = "uuid:22222222-2222-4333-8444-555555555555",
            lanPresenceState = "ON_LAN", lastLanSeenAt = now.toString())
        assertEquals(2, merge(listOf(observation)).size)
    }

    @Test fun `offline registration and UNKNOWN observations persist without history enabled`() {
        val offline = registered.copy(telemetryAvailable = false, telemetryState = "OFFLINE",
            lastTelemetryAt = now.minusSeconds(90_000).toString())
        assertEquals(offline, merge(emptyList(), listOf(offline)).single())
        val observation = registered.copy(id = "old-observation", lastLanSeenAt = now.minusSeconds(600).toString())
        assertEquals(observation.lastLanSeenAt, merge(listOf(observation)).single().lastLanSeenAt)
    }

    private class TestClock(var current: Instant) : Clock() {
        override fun instant() = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    @Test fun `PostgreSQL enrollment reports ownership restart reconnect reenrollment and LAN correlation`() {
        val url = System.getenv("AITSM_TEST_DATABASE_URL")
        assumeTrue("Requires a disposable PostgreSQL database", url != null)
        val user = System.getenv("AITSM_TEST_DATABASE_USER") ?: "postgres"
        val password = System.getenv("AITSM_TEST_DATABASE_PASSWORD") ?: ""
        val schema = "collector_test_${UUID.randomUUID().toString().replace("-", "") }"
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
        }
        val db = Database.connect("$url${if (url!!.contains('?')) '&' else '?'}currentSchema=$schema",
            driver = "org.postgresql.Driver", user = user, password = password)
        val clock = TestClock(now)
        val repository = MonitoringRepository(clock)
        val ownerId = transaction(db) {
            SchemaUtils.create(UsersTable, MonitoringCollectorsTable, CollectorEnrollmentCodesTable,
                MonitoringDevicesTable, MonitoringDeviceAddressesTable, MonitoringLanObservationsTable, DiscoveryRequestsTable)
            UsersTable.insert {
                it[fullName] = "Integration Owner"
                it[email] = "collector-test@example.invalid"
                it[company] = "Test"
                it[department] = "Test"
                it[passwordHash] = "unused-test-hash"
                it[role] = "USER"
                it[createdAt] = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            }[UsersTable.id]
        }
        fun enrollSelf(): CollectorRedeemResponse {
            val code = repository.createEnrollmentCode(ownerId, "SELF", "self-device", "Test collector")
            val self = assertNotNull(repository.redeemEnrollmentCode(code.enrollmentCode, emptyList()))
            assertNull(repository.redeemEnrollmentCode(code.enrollmentCode, emptyList()))
            return self
        }
        val self = enrollSelf()
        assertNull(repository.authenticate("invalid-token"))
        val identity = assertNotNull(repository.authenticate(self.token))
        val host = CollectorDeviceReport(deviceKey = "host:test-pc", hostname = "TEST-PC", ipAddress = "10.38.201.20",
            macAddress = "AA-BB-CC-DD-EE-01", telemetryAvailable = true, cpuUsagePercent = 12.5,
            inventory = CollectorInventoryReport(deviceUuid = registered.deviceUuid))
        fun report() = CollectorReportRequest(clock.instant().toString(), listOf(host), agentVersion = "1.3.2")
        repository.saveReport(identity.id, identity.scope, report(), clock.instant())
        val first = repository.lanDevices().single()
        assertEquals(ownerId, first.ownerUserId)
        assertEquals("Integration Owner", first.ownerName)
        assertEquals("UNKNOWN", first.lanPresenceState)
        assertEquals("AVAILABLE", first.telemetryState)
        assertEquals(12.5, first.cpuUsagePercent)
        assertEquals(first.id, MonitoringRepository(clock).lanDevices().single().id)
        clock.current = now.plusSeconds(121)
        assertEquals("STALE", repository.lanDevices().single().telemetryState)
        clock.current = now.plusSeconds(601)
        assertEquals("OFFLINE", repository.lanDevices().single().telemetryState)
        assertNull(repository.lanDevices().single().cpuUsagePercent)
        repository.saveReport(identity.id, identity.scope, report(), clock.instant())
        assertEquals("AVAILABLE", repository.lanDevices().single().telemetryState)
        val lan = repository.enroll(CollectorEnrollmentRequest("company-lan", "LAN test"))
        val lanId = UUID.fromString(lan.collectorId)
        repository.saveReport(lanId, "LAN", CollectorReportRequest(clock.instant().toString(), listOf(
            host.copy(deviceKey = "peer:10.38.201.20", hostname = "LAN peer", telemetryAvailable = false,
                inventory = null, cpuUsagePercent = null)
        )), clock.instant())
        assertEquals("ON_LAN", repository.lanDevices().single().lanPresenceState)
        assertEquals(ownerId, repository.lanDevices().single().ownerUserId)
        val replacement = enrollSelf()
        val replacementId = UUID.fromString(replacement.collectorId)
        repository.saveReport(replacementId, "SELF", report(), clock.instant())
        assertEquals(first.id, repository.lanDevices().single().id)
        assertEquals(replacement.collectorId, repository.lanDevices().single().registeredCollectorId)
        clock.current = clock.current.plusSeconds(121)
        repository.saveReport(lanId, "LAN", CollectorReportRequest(clock.instant().toString(), emptyList()), clock.instant())
        repository.saveReport(replacementId, "SELF", report(), clock.instant())
        assertEquals("OFF_LAN", repository.lanDevices().single().lanPresenceState)
        val (scanId, _) = repository.queueDiscovery()
        repository.saveReport(lanId, "LAN", CollectorReportRequest(clock.instant().toString(), emptyList(),
            discoveryRequestId = scanId.toString(), discoveryError = "Scan target limit exceeded"), clock.instant())
        assertEquals("FAILED", repository.latestDiscovery().first)
        assertEquals("Scan target limit exceeded", repository.latestDiscovery().third)
        assertTrue(repository.lanDevices().any { it.registeredCollectorId == replacement.collectorId })
        repository.revoke(replacementId)
        assertNull(repository.authenticate(replacement.token))
        assertTrue(repository.lanDevices().none { it.registeredCollectorId == replacement.collectorId })
    }
}
