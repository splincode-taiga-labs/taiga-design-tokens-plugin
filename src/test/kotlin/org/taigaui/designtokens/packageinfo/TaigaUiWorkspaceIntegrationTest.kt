package org.taigaui.designtokens.packageinfo

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume.assumeTrue
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokensPackageScanner
import org.taigaui.designtokens.psi.PsiDesignTokenSourceExtractor
import org.taigaui.designtokens.resolution.DesignTokenUnresolvedReason
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import org.taigaui.designtokens.resolution.DesignTokenValueResolver
import java.nio.file.Files
import java.nio.file.Path

class TaigaUiWorkspaceIntegrationTest : BasePlatformTestCase() {
    fun testTaigaUi4WorkspaceUsesItsOwnInstalledPackages() {
        val packageSet = resolveWorkspace(TAIGA_UI_V4_WORKSPACE)
        val packages = packageSet.sourcePackages.associateBy(DesignTokenSourcePackage::name)

        assertEquals("4.93.0", packages.getValue("@taiga-ui/core").version)
        assertEquals("0.248.0", packages.getValue("@taiga-ui/design-tokens").version)
        assertTrue(packages.getValue("@taiga-ui/core").root.startsWith(TAIGA_UI_V4_WORKSPACE))
        assertTrue(packages.getValue("@taiga-ui/design-tokens").root.startsWith(TAIGA_UI_V4_WORKSPACE))
        assertTextTokenHasNoAmbiguousReferences(packageSet)
    }

    fun testTaigaUi5WorkspaceInstallsStylesAndDesignTokensPeers() {
        val packageSet = resolveWorkspace(TAIGA_UI_V5_WORKSPACE)
        val packages = packageSet.sourcePackages.associateBy(DesignTokenSourcePackage::name)

        assertEquals("5.18.0", packages.getValue("@taiga-ui/core").version)
        assertEquals("5.18.0", packages.getValue("@taiga-ui/styles").version)
        assertEquals("0.312.0", packages.getValue("@taiga-ui/design-tokens").version)
        assertTrue(packages.getValue("@taiga-ui/styles").root.startsWith(TAIGA_UI_V5_WORKSPACE))
        assertTrue(packages.getValue("@taiga-ui/design-tokens").root.startsWith(TAIGA_UI_V5_WORKSPACE))
        assertTextTokenHasNoAmbiguousReferences(packageSet)
    }

    private fun resolveWorkspace(workspace: Path): DesignTokensPackage {
        val packageJson = workspace.resolve("node_modules/@taiga-ui/design-tokens/package.json")

        assumeTrue(
            "Run `npm run install:fixtures` to execute Taiga UI workspace integration tests.",
            Files.isRegularFile(packageJson),
        )

        return requireNotNull(DesignTokensPackageResolver().resolve(workspace.resolve("fixture.less")))
    }

    private fun assertTextTokenHasNoAmbiguousReferences(packageSet: DesignTokensPackage) {
        val scanner = DesignTokensPackageScanner(PsiDesignTokenSourceExtractor(project))
        val index =
            DesignTokenIndex.build(
                packageRoot = packageSet.realRoot,
                declarations = scanner.scan(packageSet),
            )
        val groups = DesignTokenValueResolver(index).resolveGrouped("--tui-text-secondary")

        assertTrue(groups.isNotEmpty())
        assertFalse(
            groups.any { group ->
                val result = group.representative

                result is DesignTokenValueResolution.Unresolved &&
                    result.reason is DesignTokenUnresolvedReason.AmbiguousReference
            },
        )
    }

    private companion object {
        val REPOSITORY_ROOT: Path = Path.of("").toAbsolutePath().normalize()
        val TAIGA_UI_V4_WORKSPACE: Path = REPOSITORY_ROOT.resolve("test-fixtures/taiga-ui-v4")
        val TAIGA_UI_V5_WORKSPACE: Path = REPOSITORY_ROOT.resolve("test-fixtures/taiga-ui-v5")
    }
}
