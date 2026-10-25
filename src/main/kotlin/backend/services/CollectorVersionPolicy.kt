package backend.services

import backend.config.Env
import backend.models.CollectorView

internal object CollectorVersionPolicy {
    private const val DEFAULT_LATEST_VERSION = "1.3.1"
    private const val DEFAULT_MINIMUM_VERSION = "1.3.0"

    fun apply(collector: CollectorView): CollectorView {
        val latest = Env.get("COLLECTOR_LATEST_VERSION")?.trim()?.takeIf(::isVersion) ?: DEFAULT_LATEST_VERSION
        val minimum = Env.get("COLLECTOR_MINIMUM_VERSION")?.trim()?.takeIf(::isVersion) ?: DEFAULT_MINIMUM_VERSION
        val installed = collector.agentVersion?.trim()?.takeIf(::isVersion)
        val status = when {
            installed == null -> "LEGACY"
            compareVersions(installed, minimum) < 0 -> "UNSUPPORTED"
            compareVersions(installed, latest) < 0 -> "UPDATE_AVAILABLE"
            else -> "CURRENT"
        }
        return collector.copy(
            latestAgentVersion = latest,
            minimumSupportedAgentVersion = minimum,
            versionStatus = status,
            updateRequired = status in setOf("LEGACY", "UNSUPPORTED")
        )
    }

    internal fun compareVersions(left: String, right: String): Int {
        val leftParts = numericParts(left)
        val rightParts = numericParts(right)
        val width = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until width) {
            val comparison = (leftParts.getOrElse(index) { 0 }).compareTo(rightParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun numericParts(version: String): List<Int> =
        version.trim().removePrefix("v").split('.').map { it.substringBefore('-').toIntOrNull() ?: 0 }

    private fun isVersion(value: String): Boolean = value.matches(Regex("v?\\d+(?:\\.\\d+){1,3}(?:-[A-Za-z0-9.-]+)?"))
}
