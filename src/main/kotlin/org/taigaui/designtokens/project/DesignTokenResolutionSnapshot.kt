package org.taigaui.designtokens.project

import org.taigaui.designtokens.diagnostics.PerformanceDiagnostics
import org.taigaui.designtokens.diagnostics.PerformanceMetric
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.resolution.DesignTokenValueResolver
import java.nio.file.Path

internal data class DesignTokenResolutionSnapshotInputs(
    val installedIndex: DesignTokenIndex?,
    val projectIndex: DesignTokenIndex?,
    val nameCatalogIndex: DesignTokenIndex? = null,
)

internal class DesignTokenResolutionSnapshot private constructor(
    val installedIndex: DesignTokenIndex?,
    val projectIndex: DesignTokenIndex?,
    private val nameCatalogIndex: DesignTokenIndex?,
    val mergedIndex: DesignTokenIndex?,
    val tokenNames: List<String>,
    val resolver: DesignTokenValueResolver?,
) {
    fun matches(inputs: DesignTokenResolutionSnapshotInputs): Boolean {
        val nameCatalogMatches =
            inputs.nameCatalogIndex == null || nameCatalogIndex === inputs.nameCatalogIndex

        return installedIndex === inputs.installedIndex &&
            projectIndex === inputs.projectIndex &&
            nameCatalogMatches
    }

    companion object {
        fun build(inputs: DesignTokenResolutionSnapshotInputs): DesignTokenResolutionSnapshot {
            val indexes = listOfNotNull(inputs.installedIndex, inputs.projectIndex)
            val effectiveNameCatalogIndex = inputs.nameCatalogIndex ?: inputs.installedIndex
            val mergedIndex =
                indexes
                    .takeIf { values -> values.isNotEmpty() }
                    ?.let { values ->
                        PerformanceDiagnostics.measure(PerformanceMetric.INDEX_COMPOSITION) {
                            DesignTokenIndex.merge(values)
                        }
                    }
            val tokenNames =
                buildList {
                    effectiveNameCatalogIndex?.names?.let(::addAll)
                    inputs.projectIndex?.names?.let(::addAll)
                }.distinct()
                    .sorted()

            return DesignTokenResolutionSnapshot(
                installedIndex = inputs.installedIndex,
                projectIndex = inputs.projectIndex,
                nameCatalogIndex = effectiveNameCatalogIndex,
                mergedIndex = mergedIndex,
                tokenNames = tokenNames,
                resolver = mergedIndex?.let(::DesignTokenValueResolver),
            )
        }
    }
}

internal class DesignTokenResolutionSnapshotCache {
    private val lock = Any()
    private val entries = linkedMapOf<Path, DesignTokenResolutionSnapshot>()

    val size: Int
        get() = synchronized(lock) { entries.size }

    fun getOrBuild(
        sourceFile: Path,
        inputs: DesignTokenResolutionSnapshotInputs,
    ): DesignTokenResolutionSnapshot {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val cached =
            synchronized(lock) {
                entries[normalizedSourceFile]
                    ?.takeIf { snapshot -> snapshot.matches(inputs) }
            }

        if (cached != null) {
            return cached
        }

        val snapshot = DesignTokenResolutionSnapshot.build(inputs)

        return synchronized(lock) {
            entries[normalizedSourceFile]
                ?.takeIf { cachedSnapshot -> cachedSnapshot.matches(inputs) }
                ?: snapshot.also { builtSnapshot -> entries[normalizedSourceFile] = builtSnapshot }
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }
}
