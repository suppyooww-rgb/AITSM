package backend.services

import backend.config.MonitoringCollectorsTable
import backend.repository.CollectorToken
import backend.repository.MonitoringRepository
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

object NetworkTestsTable : Table("collector_network_tests") {
    val id = uuid("id")
    val collectorId = uuid("collector_id").references(MonitoringCollectorsTable.id, onDelete = ReferenceOption.CASCADE).index()
    val status = varchar("status", 32)
    val requestedAt = datetime("requested_at")
    val expiresAt = datetime("expires_at")
    val completedAt = datetime("completed_at").nullable()
    val probeCount = integer("probe_count").default(0)
    val downloadMbps = double("download_mbps").nullable()
    val uploadMbps = double("upload_mbps").nullable()
    val latencyMs = double("latency_ms").nullable()
    val error = varchar("error", 300).nullable()
    override val primaryKey = PrimaryKey(id)
}

@Serializable data class NetworkTestView(val id: String, val collectorId: String, val collectorName: String, val scope: String, val status: String, val requestedAt: String, val completedAt: String? = null, val downloadMbps: Double? = null, val uploadMbps: Double? = null, val latencyMs: Double? = null, val error: String? = null)
@Serializable data class NetworkTestCommand(val id: String, val transferBytes: Int = NETWORK_TEST_BYTES)
@Serializable data class NetworkTestResult(val id: String, val downloadSeconds: Double? = null, val uploadSeconds: Double? = null, val latencyMs: Double? = null, val error: String? = null)
const val NETWORK_TEST_BYTES = 1_048_576

internal fun sampledMbps(seconds: Double): Double {
    require(seconds.isFinite() && seconds > 0 && seconds <= 60) { "Invalid transfer duration" }
    return NETWORK_TEST_BYTES * 8.0 / seconds / 1_000_000.0
}

class NetworkTestService(private val collectors: MonitoringRepository, private val clock: Clock = Clock.systemUTC()) {
    private fun now() = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    fun authenticate(token: String): backend.repository.CollectorIdentity {
        if (CollectorToken.collectorId(token) == null) throw InvalidCollectorTokenException()
        return collectors.authenticate(token) ?: throw InvalidCollectorTokenException()
    }

    fun queue(collectorId: UUID): NetworkTestView = transaction {
        val collector = MonitoringCollectorsTable.selectAll().where { MonitoringCollectorsTable.id eq collectorId }.singleOrNull() ?: error("Collector not found")
        require(collector[MonitoringCollectorsTable.revokedAt] == null && collector[MonitoringCollectorsTable.archivedAt] == null) { "Collector is inactive" }
        val version = collector[MonitoringCollectorsTable.agentVersion].orEmpty().split('.').take(3).map { it.toIntOrNull() ?: 0 }
        require(version.size == 3 && (version[0] > 1 || version[0] == 1 && version[1] >= 4)) { "Update this collector to 1.4.0 or newer before running a network test" }
        // Row locking serializes requests for this collector across application instances.
        MonitoringCollectorsTable.selectAll().where { MonitoringCollectorsTable.id eq collectorId }.forUpdate().single()
        val recent = NetworkTestsTable.selectAll().where { NetworkTestsTable.collectorId eq collectorId }.orderBy(NetworkTestsTable.requestedAt, SortOrder.DESC).firstOrNull()
        require(recent == null || recent[NetworkTestsTable.requestedAt].isBefore(now().minusMinutes(5))) { "Wait five minutes between tests for this collector" }
        val testId = UUID.randomUUID()
        NetworkTestsTable.insert {
            it[id] = testId; it[NetworkTestsTable.collectorId] = collectorId; it[status] = "PENDING"
            it[requestedAt] = now(); it[expiresAt] = now().plusMinutes(5); it[probeCount] = 0
        }
        view(NetworkTestsTable.selectAll().where { NetworkTestsTable.id eq testId }.single(), collector)
    }

    fun latest(): List<NetworkTestView> = transaction {
        val active = MonitoringCollectorsTable.selectAll().where { MonitoringCollectorsTable.revokedAt.isNull() and MonitoringCollectorsTable.archivedAt.isNull() }.associateBy { it[MonitoringCollectorsTable.id] }
        val tests = NetworkTestsTable.selectAll().orderBy(NetworkTestsTable.requestedAt, SortOrder.DESC).toList().groupBy { it[NetworkTestsTable.collectorId] }
        active.mapNotNull { (id, collector) -> tests[id]?.firstOrNull()?.let { view(it, collector) } }
    }

    fun next(collectorId: UUID): NetworkTestCommand? = transaction {
        val row = NetworkTestsTable.selectAll().where { (NetworkTestsTable.collectorId eq collectorId) and (NetworkTestsTable.status eq "PENDING") and (NetworkTestsTable.expiresAt greater now()) }.forUpdate().firstOrNull() ?: return@transaction null
        NetworkTestsTable.update({ NetworkTestsTable.id eq row[NetworkTestsTable.id] }) { it[status] = "RUNNING" }
        NetworkTestCommand(row[NetworkTestsTable.id].toString())
    }

    fun authorizeProbe(collectorId: UUID, testId: UUID, stage: String) = transaction {
        val row = NetworkTestsTable.selectAll().where { (NetworkTestsTable.id eq testId) and (NetworkTestsTable.collectorId eq collectorId) }.forUpdate().singleOrNull() ?: error("Network test not found")
        require(row[NetworkTestsTable.expiresAt].isAfter(now())) { "Network test expired" }
        val current = row[NetworkTestsTable.status]
        when (stage) {
            "ping" -> {
                require(current == "RUNNING" && row[NetworkTestsTable.probeCount] < 3) { "Latency probes already completed" }
                NetworkTestsTable.update({ NetworkTestsTable.id eq testId }) { it[probeCount] = row[NetworkTestsTable.probeCount] + 1 }
            }
            "download" -> {
                require(current == "RUNNING" && row[NetworkTestsTable.probeCount] == 3) { "Download probe unavailable" }
                NetworkTestsTable.update({ NetworkTestsTable.id eq testId }) { it[status] = "DOWNLOAD_STARTED" }
            }
            "upload" -> {
                require(current == "DOWNLOAD_STARTED") { "Upload probe unavailable" }
                NetworkTestsTable.update({ NetworkTestsTable.id eq testId }) { it[status] = "AWAITING_RESULT" }
            }
            else -> error("Unsupported probe")
        }
    }

    fun complete(collectorId: UUID, result: NetworkTestResult) = transaction {
        val testId = UUID.fromString(result.id)
        val row = NetworkTestsTable.selectAll().where { (NetworkTestsTable.id eq testId) and (NetworkTestsTable.collectorId eq collectorId) }.forUpdate().singleOrNull() ?: error("Network test not found")
        require(row[NetworkTestsTable.expiresAt].isAfter(now()) && row[NetworkTestsTable.status] !in setOf("COMPLETED", "FAILED")) { "Network test expired or completed" }
        if (result.error == null) {
            require(row[NetworkTestsTable.status] == "AWAITING_RESULT") { "Probe sequence incomplete" }
            val down = sampledMbps(requireNotNull(result.downloadSeconds))
            val up = sampledMbps(requireNotNull(result.uploadSeconds))
            val latency = requireNotNull(result.latencyMs)
            require(latency.isFinite() && latency in 0.0..60_000.0) { "Invalid latency" }
            NetworkTestsTable.update({ NetworkTestsTable.id eq testId }) { it[status] = "COMPLETED"; it[completedAt] = now(); it[downloadMbps] = down; it[uploadMbps] = up; it[latencyMs] = latency }
        } else NetworkTestsTable.update({ NetworkTestsTable.id eq testId }) { it[status] = "FAILED"; it[completedAt] = now(); it[error] = result.error.replace(Regex("[\\r\\n]"), " ").take(300) }
    }

    private fun view(row: ResultRow, collector: ResultRow): NetworkTestView {
        val status = row[NetworkTestsTable.status].let { if (it !in setOf("COMPLETED", "FAILED") && row[NetworkTestsTable.expiresAt].isBefore(now())) "EXPIRED" else it }
        return NetworkTestView(row[NetworkTestsTable.id].toString(), row[NetworkTestsTable.collectorId].toString(), collector[MonitoringCollectorsTable.name], collector[MonitoringCollectorsTable.scope], status,
            row[NetworkTestsTable.requestedAt].toInstant(ZoneOffset.UTC).toString(), row[NetworkTestsTable.completedAt]?.toInstant(ZoneOffset.UTC)?.toString(), row[NetworkTestsTable.downloadMbps], row[NetworkTestsTable.uploadMbps], row[NetworkTestsTable.latencyMs], row[NetworkTestsTable.error])
    }
}
