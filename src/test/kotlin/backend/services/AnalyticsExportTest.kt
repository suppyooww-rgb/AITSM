package backend.services

import backend.models.Ticket
import backend.models.TicketStatus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AnalyticsExportTest {
    @Test fun `analytics export is spreadsheet safe and excludes ticket descriptions`() {
        val ticket = Ticket(
            id = 7,
            userId = 4,
            title = "=SUM(1,1), printer \"issue\"\nsecond line",
            description = "private troubleshooting detail",
            priority = "High",
            category = "Hardware",
            status = TicketStatus.PENDING,
            assignedTo = "Support Admin",
            createdAt = "2026-10-25T01:00:00",
            updatedAt = "2026-10-25T02:00:00",
            slaRiskScore = 70,
            slaRiskLevel = "HIGH"
        )

        val csv = analyticsCsv(listOf(ticket))

        assertContains(csv, "\"Ticket ID\",\"Title\"")
        assertContains(csv, "\"'=SUM(1,1), printer \"\"issue\"\" second line\"")
        assertContains(csv, "\"7\"")
        assertContains(csv, "\"70\"")
        assertFalse(csv.contains("private troubleshooting detail"))
    }

    @Test fun `analytics route requires admin role and scoped reauthentication`() {
        val route = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/kotlin/backend/routes/AnalyticsRoutes.kt"))
        assertContains(route, "get(\"/export\")")
        assertContains(route, "requireRole(UserRole.ADMIN)")
        assertContains(route, "requireReauthentication(reauthentication, ReauthScope.SENSITIVE_EXPORT)")
        assertContains(route, "CacheControl, \"no-store\"")
    }
}

class CollectorVersionPolicyTest {
    @Test fun `collector versions compare numerically`() {
        assertEquals(-1, CollectorVersionPolicy.compareVersions("1.3.0", "1.3.1"))
        assertEquals(0, CollectorVersionPolicy.compareVersions("1.3.1", "1.3.1"))
        assertEquals(1, CollectorVersionPolicy.compareVersions("1.10.0", "1.3.1"))
        assertEquals(0, CollectorVersionPolicy.compareVersions("v1.3.1", "1.3.1"))
    }

    @Test fun `collector support policy is visible in portal contracts`() {
        val model = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/kotlin/backend/models/Models.kt"))
        val service = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/kotlin/backend/services/CollectorVersionPolicy.kt"))
        val settings = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/static/frontend/settings/js/settings.js"))
        assertContains(model, "val versionStatus: String")
        assertContains(settings, "Update or repair collector")
        assertContains(service, "COLLECTOR_LATEST_VERSION")
        assertContains(service, "COLLECTOR_MINIMUM_VERSION")
        assertContains(service, "UNSUPPORTED")
        assertContains(service, "UPDATE_AVAILABLE")
    }
}
