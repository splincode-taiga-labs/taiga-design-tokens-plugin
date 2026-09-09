package org.taigaui.designtokens.project

import org.taigaui.designtokens.diagnostics.PerformanceDiagnostics
import org.taigaui.designtokens.diagnostics.PerformanceMetric
import org.taigaui.designtokens.index.DesignTokenDeprecation
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.resolution.DesignTokenValueResolver
import java.nio.file.Path

internal data class TokenContextKey(
    val workspaceRoot: Path?,
    val projectRoot: Path?,
    val packageRoot: Path?,
    val projectEntryFiles: List<Path>,
) {
    fun normalized(): TokenContextKey =
        copy(
            workspaceRoot = workspaceRoot?.toAbsolutePath()?.normalize(),
            projectRoot = projectRoot?.toAbsolutePath()?.normalize(),
            packageRoot = packageRoot?.toAbsolutePath()?.normalize(),
            projectEntryFiles =
                projectEntryFiles
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .distinct(),
        )
}

internal data class DesignTokenCatalogEntry(
    val name: String,
    val deprecation: DesignTokenDeprecation? = null,
)

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
    val tokenCatalog: List<DesignTokenCatalogEntry>,
    val resolver: DesignTokenValueResolver?,
) {
    private val deprecationsByName =
        tokenCatalog
            .mapNotNull { entry -> entry.deprecation?.let { deprecation -> entry.name to deprecation } }
            .toMap()

    fun deprecationFor(name: String): DesignTokenDeprecation? = deprecationsByName[name]

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
            val tokenCatalog =
                tokenNames.map { name ->
                    val projectDefinesToken = inputs.projectIndex?.find(name).orEmpty().isNotEmpty()
                    val deprecation =
                        if (projectDefinesToken) {
                            inputs.projectIndex?.deprecationFor(name)
                        } else {
                            effectiveNameCatalogIndex?.deprecationFor(name)
                        }

                    DesignTokenCatalogEntry(name, deprecation)
                }

            return DesignTokenResolutionSnapshot(
                installedIndex = inputs.installedIndex,
                projectIndex = inputs.projectIndex,
                nameCatalogIndex = effectiveNameCatalogIndex,
                mergedIndex = mergedIndex,
                tokenNames = tokenNames,
                tokenCatalog = tokenCatalog,
                resolver = mergedIndex?.let(::DesignTokenValueResolver),
            )
        }
    }
}

internal class DesignTokenResolutionSnapshotCache {
    private val lock = Any()
    private val entries = linkedMapOf<TokenContextKey, DesignTokenResolutionSnapshot>()

    val size: Int
        get() = synchronized(lock) { entries.size }

    fun getOrBuild(
        contextKey: TokenContextKey,
        inputs: DesignTokenResolutionSnapshotInputs,
    ): DesignTokenResolutionSnapshot {
        val normalizedContextKey = contextKey.normalized()
        val cached =
            synchronized(lock) {
                entries[normalizedContextKey]
                    ?.takeIf { snapshot -> snapshot.matches(inputs) }
            }

        if (cached != null) {
            return cached
        }

        val snapshot = DesignTokenResolutionSnapshot.build(inputs)

        return synchronized(lock) {
            entries[normalizedContextKey]
                ?.takeIf { cachedSnapshot -> cachedSnapshot.matches(inputs) }
                ?: snapshot.also { builtSnapshot -> entries[normalizedContextKey] = builtSnapshot }
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }
}
