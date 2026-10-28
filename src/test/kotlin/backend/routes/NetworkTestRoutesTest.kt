package backend.routes

import backend.repository.MonitoringRepository
import backend.services.NetworkTestService
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkTestRoutesTest {
    @Test fun `network probes and test queue require authentication`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { networkTestRoutes(NetworkTestService(MonitoringRepository())) }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/monitoring/network-tests").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/monitoring/network-tests/00000000-0000-0000-0000-000000000001").status)
        for (path in listOf("next", "00000000-0000-0000-0000-000000000001/ping", "00000000-0000-0000-0000-000000000001/download")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/monitoring/collector/network-tests/$path").status)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/monitoring/collector/network-tests/next") { header(HttpHeaders.Authorization, "Bearer invalid") }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/monitoring/collector/network-tests/result").status)
    }
}
