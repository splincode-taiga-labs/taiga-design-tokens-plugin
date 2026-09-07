package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import java.nio.file.Path

class DesignTokenResolutionSnapshotCacheTest {
    @Test
    fun `reuses snapshot while index inputs are unchanged`() {
        val sourceFile = Path.of("build/fixtures/token-snapshot/src/app.css").toAbsolutePath().normalize()
        val installedIndex = index("--tui-installed", "red")
        val projectIndex = index("--tui-project", "blue")
        val cache = DesignTokenResolutionSnapshotCache()
        val inputs =
            DesignTokenResolutionSnapshotInputs(
                installedIndex = installedIndex,
                projectIndex = projectIndex,
                nameCatalogIndex = installedIndex,
            )

        val first = cache.getOrBuild(sourceFile, inputs)
        val second = cache.getOrBuild(sourceFile, inputs)

        assertSame(first, second)
        assertSame(first.mergedIndex, second.mergedIndex)
        assertSame(first.resolver, second.resolver)
        assertSame(first.tokenNames, second.tokenNames)
        assertEquals(listOf("--tui-installed", "--tui-project"), first.tokenNames)
        assertEquals(1, cache.size)
    }

    @Test
    fun `rebuilds snapshot when project or name catalog input changes`() {
        val sourceFile = Path.of("build/fixtures/token-snapshot/src/app.css").toAbsolutePath().normalize()
        val installedIndex = index("--tui-installed", "red")
        val firstProjectIndex = index("--tui-project", "blue")
        val secondProjectIndex = index("--tui-project", "green")
        val completeNameCatalog =
            DesignTokenIndex.merge(
                listOf(
                    installedIndex,
                    index("--tui-catalog-only", "black"),
                ),
            )
        val cache = DesignTokenResolutionSnapshotCache()
        val first =
            cache.getOrBuild(
                sourceFile,
                DesignTokenResolutionSnapshotInputs(
                    installedIndex = installedIndex,
                    projectIndex = firstProjectIndex,
                    nameCatalogIndex = installedIndex,
                ),
            )
        val projectChanged =
            cache.getOrBuild(
                sourceFile,
                DesignTokenResolutionSnapshotInputs(
                    installedIndex = installedIndex,
                    projectIndex = secondProjectIndex,
                    nameCatalogIndex = installedIndex,
                ),
            )
        val catalogChanged =
            cache.getOrBuild(
                sourceFile,
                DesignTokenResolutionSnapshotInputs(
                    installedIndex = installedIndex,
                    projectIndex = secondProjectIndex,
                    nameCatalogIndex = completeNameCatalog,
                ),
            )
        val resolutionOnly =
            cache.getOrBuild(
                sourceFile,
                DesignTokenResolutionSnapshotInputs(
                    installedIndex = installedIndex,
                    projectIndex = secondProjectIndex,
                ),
            )

        assertNotSame(first, projectChanged)
        assertNotSame(projectChanged, catalogChanged)
        assertSame(catalogChanged, resolutionOnly)
        assertSame(secondProjectIndex, catalogChanged.projectIndex)
        assertEquals(
            listOf("--tui-catalog-only", "--tui-installed", "--tui-project"),
            catalogChanged.tokenNames,
        )
        assertEquals(1, cache.size)
    }

    @Test
    fun `drops resolver memoization when snapshot input is replaced`() {
        val sourceFile = Path.of("build/fixtures/token-snapshot/src/app.css").toAbsolutePath().normalize()
        val firstIndex = index("--tui-token", "red")
        val secondIndex = index("--tui-token", "blue")
        val cache = DesignTokenResolutionSnapshotCache()
        val first =
            cache.getOrBuild(
                sourceFile,
                DesignTokenResolutionSnapshotInputs(
                    installedIndex = firstIndex,
                    projectIndex = null,
                    nameCatalogIndex = firstIndex,
                ),
            )
        val firstResolver = requireNotNull(first.resolver)
        val firstVariant = requireNotNull(first.mergedIndex).find("--tui-token").single()

        firstResolver.resolve(firstVariant)

        assertEquals(1, firstResolver.parsedValueCacheSize)
        assertEquals(1, firstResolver.resolutionCacheSize)

        val replaced =
            cache.getOrBuild(
                sourceFile,
                DesignTokenResolutionSnapshotInputs(
                    installedIndex = secondIndex,
                    projectIndex = null,
                    nameCatalogIndex = secondIndex,
                ),
            )
        val replacedResolver = requireNotNull(replaced.resolver)
        val replacedVariant = requireNotNull(replaced.mergedIndex).find("--tui-token").single()

        assertNotSame(first, replaced)
        assertNotSame(firstResolver, replacedResolver)
        assertEquals(0, replacedResolver.parsedValueCacheSize)
        assertEquals(0, replacedResolver.resolutionCacheSize)

        val replacedResult =
            replacedResolver.resolve(replacedVariant).result as DesignTokenValueResolution.Resolved

        assertEquals("blue", replacedResult.value)
        assertEquals(1, replacedResolver.parsedValueCacheSize)
        assertEquals(1, replacedResolver.resolutionCacheSize)
    }

    private fun index(
        name: String,
        value: String,
    ): DesignTokenIndex {
        val packageRoot = Path.of("build/fixtures/token-snapshot/package").toAbsolutePath().normalize()

        return DesignTokenIndex.build(
            packageRoot = packageRoot,
            declarations =
                listOf(
                    DesignTokenDeclaration(
                        name = name,
                        value = value,
                        sourceFile = packageRoot.resolve("tokens.css"),
                        line = 1,
                    ),
                ),
        )
    }
}
