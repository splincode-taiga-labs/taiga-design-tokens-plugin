package org.taigaui.designtokens.icons

import java.nio.file.Path

internal fun interface IconCatalogClock {
    fun nowMillis(): Long
}

internal data class IconCatalogCacheLookup(
    val catalog: IconCatalog,
    val isFresh: Boolean,
)

internal class IconCatalogCache(
    private val clock: IconCatalogClock = IconCatalogClock(System::currentTimeMillis),
) {
    private val lock = Any()
    private val entries = linkedMapOf<Path, Entry>()
    private val generations = linkedMapOf<Path, Long>()

    val scopeRoots: Set<Path>
        get() = synchronized(lock) { entries.keys.toSet() }

    fun lookup(scopeRoot: Path): IconCatalogCacheLookup? {
        val normalizedScope = scopeRoot.normalized()

        return synchronized(lock) {
            entries[normalizedScope]?.let { entry ->
                IconCatalogCacheLookup(
                    catalog = entry.catalog,
                    isFresh = entry.expiresAtMillis?.let { expiresAt -> clock.nowMillis() < expiresAt } ?: true,
                )
            }
        }
    }

    fun generation(scopeRoot: Path): Long =
        synchronized(lock) {
            generations[scopeRoot.normalized()] ?: 0L
        }

    fun publish(
        scopeRoot: Path,
        generation: Long,
        result: IconCatalogLoadResult,
    ): Boolean {
        val normalizedScope = scopeRoot.normalized()

        return synchronized(lock) {
            if ((generations[normalizedScope] ?: 0L) != generation) {
                false
            } else {
                val retainedCatalog =
                    entries[normalizedScope]
                        ?.takeIf { entry ->
                            result.cachePolicy == IconCatalogCachePolicy.REMOTE_RETRY &&
                                entry.hasSuccessfulRemoteCatalog
                        }?.catalog

                entries[normalizedScope] =
                    Entry(
                        catalog = retainedCatalog ?: result.catalog,
                        expiresAtMillis =
                            result.cachePolicy.maxAge
                                ?.toMillis()
                                ?.let { maxAgeMillis -> clock.nowMillis() + maxAgeMillis },
                        hasSuccessfulRemoteCatalog =
                            result.cachePolicy == IconCatalogCachePolicy.REMOTE_SUCCESS ||
                                retainedCatalog != null,
                    )
                true
            }
        }
    }

    fun invalidate(scopeRoot: Path): Boolean {
        val normalizedScope = scopeRoot.normalized()

        return synchronized(lock) {
            val removed = entries.remove(normalizedScope) != null

            generations[normalizedScope] = (generations[normalizedScope] ?: 0L) + 1L
            removed
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            generations.clear()
        }
    }

    private data class Entry(
        val catalog: IconCatalog,
        val expiresAtMillis: Long?,
        val hasSuccessfulRemoteCatalog: Boolean,
    )
}

internal object IconCatalogInvalidation {
    fun isAffected(
        context: IconCatalogContext,
        changedPath: Path,
    ): Boolean {
        val changed = changedPath.normalized()
        val metadataOrPackageChanged =
            context.invalidationRoots.any { root ->
                val normalizedRoot = root.normalized()

                changed == normalizedRoot ||
                    changed.startsWith(normalizedRoot) ||
                    normalizedRoot.startsWith(changed)
            }

        return metadataOrPackageChanged ||
            context.physicalScopeRoot?.let { scopeRoot -> isAffected(scopeRoot, changed) } == true
    }

    fun isAffected(
        scopeRoot: Path,
        changedPath: Path,
    ): Boolean {
        val scope = scopeRoot.normalized()
        val changed = changedPath.normalized()
        val projectRoot = scope.parent?.parent
        val projectPackageChanged =
            projectRoot != null && changed == projectRoot.resolve(PACKAGE_JSON).normalized()

        return when {
            scope == changed || scope.startsWith(changed) -> true
            projectPackageChanged -> true
            !changed.startsWith(scope) -> false
            else -> isAffectedWithinScope(scope.relativize(changed), changed)
        }
    }

    private fun isAffectedWithinScope(
        relative: Path,
        changedPath: Path,
    ): Boolean {
        val packageName = relative.getName(0).toString()
        val fileName = changedPath.fileName?.toString()?.lowercase()
        val packageRootChanged = relative.nameCount == 1
        val packageManifestChanged = fileName == PACKAGE_JSON
        val iconSourceChanged =
            relative.nameCount > 1 &&
                relative.getName(1).toString() == SRC_DIRECTORY &&
                (fileName?.endsWith(SVG_EXTENSION) == true || '.' !in (fileName ?: ""))

        return when (packageName) {
            ICONS_PACKAGE,
            TDS_ICONS_PACKAGE,
            -> packageRootChanged || packageManifestChanged || iconSourceChanged

            PROPRIETARY_PACKAGE -> packageRootChanged || packageManifestChanged
            else -> false
        }
    }

    private const val SRC_DIRECTORY = "src"
    private const val ICONS_PACKAGE = "icons"
    private const val TDS_ICONS_PACKAGE = "tds-icons"
    private const val PROPRIETARY_PACKAGE = "proprietary"
    private const val PACKAGE_JSON = "package.json"
    private const val SVG_EXTENSION = ".svg"
}

private fun Path.normalized(): Path = toAbsolutePath().normalize()
