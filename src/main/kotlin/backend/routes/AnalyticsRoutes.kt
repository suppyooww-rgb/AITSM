package backend.routes

import backend.models.UserRole
import backend.models.TicketStatus
import backend.models.AnalyticsResponse
import backend.security.requireRole
import backend.security.requireReauthentication
import backend.security.userId
import backend.services.AIService
import backend.services.MonitoringService
import backend.services.ReauthScope
import backend.services.ReauthenticationService
import backend.services.TicketService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.ZoneOffset

fun Route.analyticsRoutes(
    ticketService: TicketService,
    monitoringService: MonitoringService,
    aiService: AIService,
    reauthentication: ReauthenticationService
) {
    route("/api/analytics") {
        get("/export") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            if (!call.requireReauthentication(reauthentication, ReauthScope.SENSITIVE_EXPORT)) return@get
            val actorId = call.userId() ?: return@get
            val csv = ticketService.exportAnalyticsCsv(actorId)
            val date = LocalDate.now(ZoneOffset.UTC)
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"AITSM-analytics-$date.csv\"")
            call.respondText(csv, ContentType.Text.CSV)
        }
        get("/ticket-trends") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            val tickets = ticketService.list(null, true, 500, 0).items
            val total = tickets.size
            val open = tickets.count { it.status == TicketStatus.OPEN }
            val resolved = tickets.count { it.status == TicketStatus.RESOLVED }
            call.respond(AnalyticsResponse("ticket-trends", aiService.ticketTrendInsights(total, open, resolved)))
        }
        get("/system-health") {
            if (!call.requireRole(UserRole.ADMIN)) return@get
            val devices = monitoringService.devices().filter { it.telemetryAvailable && !it.stale }
            val avgCpu = devices.mapNotNull { it.cpuUsagePercent }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
            val health = (100 - avgCpu).coerceAtLeast(0)
            call.respond(AnalyticsResponse("system-health", listOf(
                mapOf("insight" to "abnormal spikes", "value" to if (avgCpu > 75) "Detected" else "Stable"),
                mapOf("insight" to "system health score", "value" to "$health"),
                mapOf("insight" to "usage stability", "value" to if (avgCpu < 65) "High" else "Moderate")
            )))
        }
    }
}
