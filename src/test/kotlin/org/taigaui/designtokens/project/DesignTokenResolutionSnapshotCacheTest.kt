package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.DesignTokenIndex
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
