package org.taigaui.designtokens.project

import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.packageinfo.DesignTokenSourcePackage
import org.taigaui.designtokens.packageinfo.DesignTokensPackage
import java.nio.file.Path

internal fun interface DesignTokenIndexBuilder {
    fun build(designTokensPackage: DesignTokensPackage): DesignTokenIndex
}

internal data class DesignTokensPackageIdentity(
    val realRoot: Path,
    val version: String,
) {
    companion object {
        fun from(designTokensPackage: DesignTokensPackage): DesignTokensPackageIdentity =
            DesignTokensPackageIdentity(
                realRoot = designTokensPackage.realRoot.toAbsolutePath().normalize(),
                version = designTokensPackage.version,
            )
    }
}

internal class DesignTokenIndexCache(
    private val indexBuilder: DesignTokenIndexBuilder,
) {
    private val lock = Any()
    private val entries = linkedMapOf<DesignTokensPackageIdentity, CacheEntry>()

    val size: Int
        get() = synchronized(lock) { entries.size }

    fun getOrBuild(designTokensPackage: DesignTokensPackage): DesignTokenIndex =
        synchronized(lock) {
            val normalizedPackage = designTokensPackage.normalized()
            val identity = DesignTokensPackageIdentity.from(normalizedPackage)

            removeReplacedPackages(
                identity = identity,
                logicalRoot = normalizedPackage.root,
            )

            entries[identity]?.let { entry ->
                entry.logicalRoots.add(normalizedPackage.root)
                entry.packageRoots.addAll(normalizedPackage.cacheRoots())

                return@synchronized entry.index
            }

            val index = indexBuilder.build(normalizedPackage)

            entries[identity] =
                CacheEntry(
                    index = index,
                    logicalRoots = linkedSetOf(normalizedPackage.root),
                    packageRoots = normalizedPackage.cacheRoots().toMutableSet(),
                )

            index
        }

    fun invalidate(changedPaths: Collection<Path>): Int =
        synchronized(lock) {
            val normalizedPaths =
                changedPaths
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .distinct()

            val sizeBefore = entries.size

            entries.entries.removeIf { (_, entry) ->
                normalizedPaths.any(entry::isAffectedBy)
            }

            sizeBefore - entries.size
        }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }

    private fun removeReplacedPackages(
        identity: DesignTokensPackageIdentity,
        logicalRoot: Path,
    ) {
        entries.entries.removeIf { (cachedIdentity, entry) ->
            val replacedVersion =
                cachedIdentity.realRoot == identity.realRoot &&
                    cachedIdentity.version != identity.version
            val replacedTarget =
                logicalRoot in entry.logicalRoots &&
                    cachedIdentity != identity

            replacedVersion || replacedTarget
        }
    }

    private data class CacheEntry(
        val index: DesignTokenIndex,
        val logicalRoots: MutableSet<Path>,
        val packageRoots: MutableSet<Path>,
    ) {
        fun isAffectedBy(changedPath: Path): Boolean =
            (logicalRoots + packageRoots).any { packageRoot ->
                val packageRootChanged = changedPath == packageRoot
                val packageAncestorChanged = packageRoot.startsWith(changedPath)
                val relevantPackageFileChanged =
                    changedPath.startsWith(packageRoot) &&
                        changedPath.isRelevantPackagePath()

                packageRootChanged || packageAncestorChanged || relevantPackageFileChanged
            }
    }

    private companion object {
        val STYLESHEET_EXTENSIONS = setOf("css", "less", "scss")

        fun DesignTokensPackage.normalized(): DesignTokensPackage =
            copy(
                root = root.toAbsolutePath().normalize(),
                realRoot = realRoot.toAbsolutePath().normalize(),
                sourcePackages = sourcePackages.map(DesignTokenSourcePackage::normalized),
            )

        fun DesignTokenSourcePackage.normalized(): DesignTokenSourcePackage =
            copy(
                root = root.toAbsolutePath().normalize(),
                realRoot = realRoot.toAbsolutePath().normalize(),
                sourceRoots =
                    sourceRoots
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize),
            )

        fun DesignTokensPackage.cacheRoots(): Set<Path> =
            buildSet {
                add(root)
                add(realRoot)
                effectiveSourcePackages.forEach { sourcePackage ->
                    add(sourcePackage.root)
                    add(sourcePackage.realRoot)
                    addAll(sourcePackage.sourceRoots)
                }
            }

        fun Path.isRelevantPackagePath(): Boolean {
            val fileName = fileName?.toString()?.lowercase() ?: return true
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")

            return fileName == "package.json" ||
                extension in STYLESHEET_EXTENSIONS ||
                '.' !in fileName
        }
    }
}
