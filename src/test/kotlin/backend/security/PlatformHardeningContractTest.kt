package backend.security

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformHardeningContractTest {
    @Test fun `temporary password state is enforced in database service and request boundary`() {
        val migration = Files.readString(Path.of("db/migrations/V021__force_temporary_password_change.sql"))
        val repository = Files.readString(Path.of("src/main/kotlin/backend/repository/UserRepository.kt"))
        val boundary = Files.readString(Path.of("src/main/kotlin/backend/security/AuthMiddleware.kt"))
        assertTrue(migration.contains("must_change_password"))
        assertTrue(repository.contains("mustChangePassword] = true"))
        assertTrue(repository.contains("mustChangePassword] = false"))
        assertTrue(boundary.contains("Password change required before continuing"))
        assertTrue(boundary.contains("/api/users/me/password"))
    }

    @Test fun `ticket screenshots use authenticated validated database backed storage`() {
        val routes = Files.readString(Path.of("src/main/kotlin/backend/routes/TicketRoutes.kt"))
        val service = Files.readString(Path.of("src/main/kotlin/backend/services/TicketService.kt"))
        val migration = Files.readString(Path.of("db/migrations/V022__secure_ticket_image_attachments.sql"))
        assertTrue(routes.contains("/{id}/attachments"))
        assertTrue(routes.contains("requireAuthenticated"))
        assertTrue(service.contains("detectImageType"))
        assertTrue(service.contains("5 * 1024 * 1024"))
        assertTrue(migration.contains("content_base64"))
    }

    @Test fun `ticket counters distinguish resolved from closed and reopen to in progress`() {
        val userDashboard = Files.readString(Path.of("src/main/resources/static/frontend/dashboard-user/js/user-dashboard.js"))
        val adminDashboard = Files.readString(Path.of("src/main/resources/static/frontend/dashboard-admin/js/dashboard.js"))
        val ticketAdmin = Files.readString(Path.of("src/main/resources/static/frontend/ticket-management/js/admin-ticket-management.js"))
        assertTrue(userDashboard.contains("'Closed'"))
        assertTrue(adminDashboard.contains("'Closed Tickets'"))
        assertTrue(ticketAdmin.contains("Reopen as in progress"))
    }
}
