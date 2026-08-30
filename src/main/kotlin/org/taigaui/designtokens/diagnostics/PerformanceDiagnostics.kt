package org.taigaui.designtokens.diagnostics

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal enum class PerformanceMetric(
    val key: String,
) {
    PACKAGE_SCAN("package-scan"),
    PROJECT_GRAPH_BUILD("project-graph-build"),
    PSI_EXTRACTION("psi-extraction"),
    INDEX_COMPOSITION("index-composition"),
    VALUE_RESOLUTION("value-resolution"),
    ICON_CATALOG_LOAD("icon-catalog-load"),
}

internal data class PerformanceMeasurement(
    val count: Long,
    val totalNanos: Long,
    val maxNanos: Long,
)

internal object PerformanceDiagnostics {
    private val measurements = ConcurrentHashMap<PerformanceMetric, MutableMeasurement>()

    @Volatile
    private var enabledOverride: Boolean? = null

    fun <T> measure(
        metric: PerformanceMetric,
        operation: () -> T,
    ): T {
        if (!isEnabled()) {
            return operation()
        }

        val startedAt = System.nanoTime()

        return try {
            operation()
        } finally {
            val elapsedNanos = System.nanoTime() - startedAt

            measurements
                .computeIfAbsent(metric) { MutableMeasurement() }
                .record(elapsedNanos)
            LOG.info("Taiga UI performance: metric=${metric.key} durationNanos=$elapsedNanos")
        }
    }

    fun snapshot(): Map<PerformanceMetric, PerformanceMeasurement> =
        PerformanceMetric.entries.associateWith { metric ->
            measurements[metric]?.snapshot() ?: PerformanceMeasurement(0, 0, 0)
        }

    internal fun reset() {
        measurements.clear()
    }

    internal fun setEnabledForTests(enabled: Boolean?) {
        enabledOverride = enabled
    }

    private fun isEnabled(): Boolean =
        enabledOverride ?: System.getProperty(ENABLED_PROPERTY)?.toBooleanStrictOrNull() ?: false

    private class MutableMeasurement {
        private val count = AtomicLong()
        private val totalNanos = AtomicLong()
        private val maxNanos = AtomicLong()

        fun record(elapsedNanos: Long) {
            count.incrementAndGet()
            totalNanos.addAndGet(elapsedNanos)
            maxNanos.updateAndGet { current -> maxOf(current, elapsedNanos) }
        }

        fun snapshot(): PerformanceMeasurement =
            PerformanceMeasurement(
                count = count.get(),
                totalNanos = totalNanos.get(),
                maxNanos = maxNanos.get(),
            )
    }

    private const val ENABLED_PROPERTY = "taiga.design.tokens.performanceDiagnostics"
    private val LOG = Logger.getInstance(PerformanceDiagnostics::class.java)
}
