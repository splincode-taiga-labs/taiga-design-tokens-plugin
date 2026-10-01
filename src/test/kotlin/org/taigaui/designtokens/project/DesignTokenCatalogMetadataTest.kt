package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
import java.nio.file.Path

class DesignTokenCatalogMetadataTest {
    @Test
    fun `classifies project package override and catalog-only tokens deterministically`() {
        val root = Path.of("build/fixtures/catalog-metadata").toAbsolutePath().normalize()
        val installed =
            index(
                root.resolve("installed"),
                declaration(root, "--tui-installed", "@taiga-ui/design-tokens"),
                declaration(root, "--tui-override", "@taiga-ui/design-tokens"),
            )
        val project =
            index(
                root.resolve("project"),
                declaration(root, "--tui-project", PROJECT_STYLES_PACKAGE),
                declaration(root, "--tui-override", PROJECT_STYLES_PACKAGE),
            )
        val catalog =
            index(
                root.resolve("catalog"),
                declaration(root, "--tui-installed", "@taiga-ui/design-tokens"),
                declaration(root, "--tui-override", "@taiga-ui/design-tokens"),
                declaration(root, "--tui-catalog-only", "@taiga-ui/proprietary"),
            )
        val snapshot =
            DesignTokenResolutionSnapshot.build(
                DesignTokenResolutionSnapshotInputs(
                    installedIndex = installed,
                    projectIndex = project,
                    nameCatalogIndex = catalog,
                ),
            )

        assertEquals(
            listOf(
                "--tui-catalog-only",
                "--tui-installed",
                "--tui-override",
                "--tui-project",
            ),
            snapshot.tokenCatalog.map(DesignTokenCatalogEntry::name),
        )

        assertEntry(
            snapshot,
            "--tui-project",
            source = DesignTokenCatalogSource.PROJECT,
            label = "project",
            effective = true,
        )
        assertEntry(
            snapshot,
            "--tui-override",
            source = DesignTokenCatalogSource.PROJECT_OVERRIDE,
            label = "project override",
            effective = true,
        )
        assertEntry(
            snapshot,
            "--tui-installed",
            source = DesignTokenCatalogSource.INSTALLED_PACKAGE,
            label = "@taiga-ui/design-tokens",
            effective = true,
        )
        assertEntry(
            snapshot,
            "--tui-catalog-only",
            source = DesignTokenCatalogSource.CATALOG_ONLY,
            label = "@taiga-ui/proprietary · catalog",
            effective = false,
        )
    }

    @Test
    fun `orders completion priority from project override to catalog-only`() {
        val entries =
            listOf(
                DesignTokenCatalogEntry(
                    name = "--tui-catalog",
                    source = DesignTokenCatalogSource.CATALOG_ONLY,
                ),
                DesignTokenCatalogEntry(
                    name = "--tui-installed",
                    source = DesignTokenCatalogSource.INSTALLED_PACKAGE,
                ),
                DesignTokenCatalogEntry(
                    name = "--tui-project",
                    source = DesignTokenCatalogSource.PROJECT,
                ),
                DesignTokenCatalogEntry(
                    name = "--tui-override",
                    source = DesignTokenCatalogSource.PROJECT_OVERRIDE,
                ),
            )

        assertEquals(
            listOf(
                DesignTokenCatalogSource.PROJECT_OVERRIDE,
                DesignTokenCatalogSource.PROJECT,
                DesignTokenCatalogSource.INSTALLED_PACKAGE,
                DesignTokenCatalogSource.CATALOG_ONLY,
            ),
            entries
                .sortedByDescending(DesignTokenCatalogEntry::completionPriority)
                .map(DesignTokenCatalogEntry::source),
        )
    }

    private fun assertEntry(
        snapshot: DesignTokenResolutionSnapshot,
        name: String,
        source: DesignTokenCatalogSource,
        label: String,
        effective: Boolean,
    ) {
        val entry = snapshot.tokenCatalog.single { current -> current.name == name }

        assertEquals(source, entry.source)
        assertEquals(label, entry.sourceLabel)

        if (effective) {
            assertTrue(entry.effective)
        } else {
            assertFalse(entry.effective)
        }
    }

    private fun index(
        root: Path,
        vararg declarations: DesignTokenDeclaration,
    ): DesignTokenIndex =
        DesignTokenIndex.build(
            packageRoot = root,
            declarations = declarations.toList(),
        )

    private fun declaration(
        root: Path,
        name: String,
        packageName: String,
    ): DesignTokenDeclaration =
        DesignTokenDeclaration(
            name = name,
            value = "red",
            sourceFile = root.resolve(packageName.substringAfterLast('/') + ".less"),
            line = 1,
            packageName = packageName,
            packageRoot = root,
        )
}
