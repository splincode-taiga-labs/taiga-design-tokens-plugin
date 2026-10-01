package org.taigaui.designtokens.project

import org.taigaui.designtokens.diagnostics.PerformanceDiagnostics
import org.taigaui.designtokens.diagnostics.PerformanceMetric
import org.taigaui.designtokens.index.DesignTokenDeprecation
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokenVariant
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

internal enum class DesignTokenCatalogSource {
    PROJECT_OVERRIDE,
    PROJECT,
    INSTALLED_PACKAGE,
    CATALOG_ONLY,
}

internal data class DesignTokenCatalogEntry(
    val name: String,
    val deprecation: DesignTokenDeprecation? = null,
    val source: DesignTokenCatalogSource,
    val packageName: String? = null,
) {
    val effective: Boolean
        get() = source != DesignTokenCatalogSource.CATALOG_ONLY

    val sourceLabel: String
        get() =
            when (source) {
                DesignTokenCatalogSource.PROJECT_OVERRIDE -> "project override"
                DesignTokenCatalogSource.PROJECT -> "project"
                DesignTokenCatalogSource.INSTALLED_PACKAGE -> packageName ?: "Taiga UI"
                DesignTokenCatalogSource.CATALOG_ONLY ->
                    packageName
                        ?.let { name -> "$name · catalog" }
                        ?: "catalog"
            }

    val completionPriority: Double
        get() =
            when (source) {
                DesignTokenCatalogSource.PROJECT_OVERRIDE -> PROJECT_OVERRIDE_PRIORITY
                DesignTokenCatalogSource.PROJECT -> PROJECT_PRIORITY
                DesignTokenCatalogSource.INSTALLED_PACKAGE -> INSTALLED_PRIORITY
                DesignTokenCatalogSource.CATALOG_ONLY -> CATALOG_ONLY_PRIORITY
            }

    private companion object {
        const val PROJECT_OVERRIDE_PRIORITY = 40.0
        const val PROJECT_PRIORITY = 30.0
        const val INSTALLED_PRIORITY = 20.0
        const val CATALOG_ONLY_PRIORITY = 10.0
    }
}

internal data class DesignTokenResolutionSnapshotInputs(
    val installedIndex: DesignTokenIndex?,
    val projectIndex: DesignTokenIndex?,
    val nameCatalogIndex: DesignTokenIndex? = null,
)

internal class DesignTokenResolutionSnapshot private constructor(
    private val inputs: DesignTokenResolutionSnapshotInputs,
    private val effectiveNameCatalogIndex: DesignTokenIndex?,
    val mergedIndex: DesignTokenIndex?,
    val tokenCatalog: List<DesignTokenCatalogEntry>,
    val resolver: DesignTokenValueResolver?,
) {
    val installedIndex: DesignTokenIndex?
        get() = inputs.installedIndex

    val projectIndex: DesignTokenIndex?
        get() = inputs.projectIndex

    val tokenNames: List<String> = tokenCatalog.map(DesignTokenCatalogEntry::name)

    private val deprecationsByName =
        tokenCatalog
            .mapNotNull { entry -> entry.deprecation?.let { deprecation -> entry.name to deprecation } }
            .toMap()

    fun deprecationFor(name: String): DesignTokenDeprecation? = deprecationsByName[name]

    fun matches(candidate: DesignTokenResolutionSnapshotInputs): Boolean {
        val nameCatalogMatches =
            candidate.nameCatalogIndex == null || effectiveNameCatalogIndex === candidate.nameCatalogIndex

        return installedIndex === candidate.installedIndex &&
            projectIndex === candidate.projectIndex &&
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
                    catalogEntry(
                        name = name,
                        inputs = inputs,
                        effectiveNameCatalogIndex = effectiveNameCatalogIndex,
                    )
                }

            return DesignTokenResolutionSnapshot(
                inputs = inputs,
                effectiveNameCatalogIndex = effectiveNameCatalogIndex,
                mergedIndex = mergedIndex,
                tokenCatalog = tokenCatalog,
                resolver = mergedIndex?.let(::DesignTokenValueResolver),
            )
        }

        private fun catalogEntry(
            name: String,
            inputs: DesignTokenResolutionSnapshotInputs,
            effectiveNameCatalogIndex: DesignTokenIndex?,
        ): DesignTokenCatalogEntry {
            val projectVariants = inputs.projectIndex?.find(name).orEmpty()
            val installedVariants = inputs.installedIndex?.find(name).orEmpty()
            val catalogVariants = effectiveNameCatalogIndex?.find(name).orEmpty()
            val source =
                when {
                    projectVariants.isNotEmpty() && installedVariants.isNotEmpty() ->
                        DesignTokenCatalogSource.PROJECT_OVERRIDE

                    projectVariants.isNotEmpty() ->
                        DesignTokenCatalogSource.PROJECT

                    installedVariants.isNotEmpty() ->
                        DesignTokenCatalogSource.INSTALLED_PACKAGE

                    else ->
                        DesignTokenCatalogSource.CATALOG_ONLY
                }
            val sourceVariants =
                if (source == DesignTokenCatalogSource.CATALOG_ONLY) {
                    catalogVariants
                } else {
                    installedVariants
                }
            val projectDefinesToken = projectVariants.isNotEmpty()
            val deprecation =
                if (projectDefinesToken) {
                    inputs.projectIndex?.deprecationFor(name)
                } else {
                    effectiveNameCatalogIndex?.deprecationFor(name)
                }

            return DesignTokenCatalogEntry(
                name = name,
                deprecation = deprecation,
                source = source,
                packageName = sourceVariants.primaryPackageName(),
            )
        }

        private fun List<DesignTokenVariant>.primaryPackageName(): String? =
            asSequence()
                .flatMap { variant -> variant.origins.asSequence() }
                .mapNotNull { origin -> origin.packageName }
                .distinct()
                .firstOrNull()
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
