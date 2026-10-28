package backend.routes

import backend.models.ApiErrorResponse
import backend.models.UserRole
import backend.security.requireRole
import backend.services.*
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.security.SecureRandom
import java.util.UUID

private val networkProbePayload = ByteArray(NETWORK_TEST_BYTES).also { SecureRandom().nextBytes(it) }

private suspend fun ApplicationCall.networkCollector(service: NetworkTestService): UUID? {
    val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ").orEmpty()
    if (token.isBlank()) {
        respond(HttpStatusCode.Unauthorized, ApiErrorResponse(401, "Collector authentication required"))
        return null
    }
    return try { service.authenticate(token).id } catch (_: InvalidCollectorTokenException) {
        respond(HttpStatusCode.Unauthorized, ApiErrorResponse(401, "Collector authentication required")); null
    }
}

fun Route.networkTestRoutes(service: NetworkTestService) {
    get("/api/monitoring/network-tests") {
        if (call.requireRole(UserRole.ADMIN)) call.respond(service.latest())
    }
    post("/api/monitoring/network-tests/{collectorId}") {
        if (!call.requireRole(UserRole.ADMIN)) return@post
        call.respond(HttpStatusCode.Accepted, service.queue(UUID.fromString(call.parameters["collectorId"])))
    }
    route("/api/monitoring/collector/network-tests") {
        get("/next") {
            val collector = call.networkCollector(service) ?: return@get
            val job = service.next(collector)
            if (job == null) call.respond(HttpStatusCode.NoContent) else call.respond(job)
        }
        get("/{id}/ping") {
            val collector = call.networkCollector(service) ?: return@get
            service.authorizeProbe(collector, UUID.fromString(call.parameters["id"]), "ping")
            call.respondText("ok", ContentType.Text.Plain)
        }
        get("/{id}/download") {
            val collector = call.networkCollector(service) ?: return@get
            service.authorizeProbe(collector, UUID.fromString(call.parameters["id"]), "download")
            call.respondBytes(networkProbePayload, ContentType.Application.OctetStream)
        }
        post("/{id}/upload") {
            val collector = call.networkCollector(service) ?: return@post
            require(call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull() == NETWORK_TEST_BYTES.toLong()) { "Network probe must contain exactly $NETWORK_TEST_BYTES bytes" }
            service.authorizeProbe(collector, UUID.fromString(call.parameters["id"]), "upload")
            val bytes = call.receiveChannel().readRemaining(NETWORK_TEST_BYTES.toLong() + 1).readByteArray()
            require(bytes.size == NETWORK_TEST_BYTES) { "Incomplete network upload" }
            call.respond(mapOf("receivedBytes" to bytes.size))
        }
        post("/result") {
            val collector = call.networkCollector(service) ?: return@post
            service.complete(collector, call.receive<NetworkTestResult>())
            call.respond(mapOf("accepted" to true))
        }
    }
}
