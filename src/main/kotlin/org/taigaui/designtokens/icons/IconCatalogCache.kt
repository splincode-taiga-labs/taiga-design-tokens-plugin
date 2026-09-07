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
                entries[normalizedScope] =
                    Entry(
                        catalog = result.catalog,
                        expiresAtMillis =
                            result.cachePolicy.maxAge
                                ?.toMillis()
                                ?.let { maxAgeMillis -> clock.nowMillis() + maxAgeMillis },
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
    )
}

internal object IconCatalogInvalidation {
    fun isAffected(
        scopeRoot: Path,
        changedPath: Path,
    ): Boolean {
        val scope = scopeRoot.normalized()
        val changed = changedPath.normalized()

        if (scope == changed || scope.startsWith(changed)) {
            return true
        }

        val projectRoot = scope.parent?.parent

        if (projectRoot != null && changed == projectRoot.resolve(PACKAGE_JSON).normalized()) {
            return true
        }

        if (!changed.startsWith(scope)) {
            return false
        }

        val relative = scope.relativize(changed)

        if (relative.nameCount == 0) {
            return true
        }

        val packageName = relative.getName(0).toString()
        val fileName = changed.fileName?.toString()?.lowercase()

        return when (packageName) {
            ICONS_PACKAGE,
            TDS_ICONS_PACKAGE,
            -> relative.nameCount == 1 || fileName == PACKAGE_JSON || relative.startsWith(SRC_DIRECTORY)

            PROPRIETARY_PACKAGE -> relative.nameCount == 1 || fileName == PACKAGE_JSON
            else -> false
        }
    }

    private val SRC_DIRECTORY = Path.of("src")
    private const val ICONS_PACKAGE = "icons"
    private const val TDS_ICONS_PACKAGE = "tds-icons"
    private const val PROPRIETARY_PACKAGE = "proprietary"
    private const val PACKAGE_JSON = "package.json"
}

private fun Path.normalized(): Path = toAbsolutePath().normalize()
