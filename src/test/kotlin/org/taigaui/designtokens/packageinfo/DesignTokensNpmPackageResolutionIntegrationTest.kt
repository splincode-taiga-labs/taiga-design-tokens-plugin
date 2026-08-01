package org.taigaui.designtokens.packageinfo

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.index.DesignTokensPackageScanner
import org.taigaui.designtokens.psi.PsiDesignTokenSourceExtractor
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import org.taigaui.designtokens.resolution.DesignTokenValueResolver

class DesignTokensNpmPackageResolutionIntegrationTest : BasePlatformTestCase() {
    private lateinit var fixture: InstalledDesignTokensPackageFixture
    private lateinit var packageInfo: DesignTokensPackage
    private lateinit var scanner: DesignTokensPackageScanner

    override fun setUp() {
        super.setUp()
        fixture = InstalledDesignTokensPackageFixture()
        packageInfo = fixture.resolve()
        scanner =
            DesignTokensPackageScanner(
                sourceExtractor = PsiDesignTokenSourceExtractor(project),
            )
    }

    fun testResolvesKnownLightBackgroundTokenToTerminalColor() {
        val index =
            DesignTokenIndex.build(
                packageRoot = packageInfo.realRoot,
                declarations = scanner.scan(packageInfo),
            )
        val variant =
            index
                .find("--tui-background-base")
                .single { candidate ->
                    candidate.context == LIGHT_DESKTOP &&
                        candidate.rawValue == "var(--tui-const-white)"
                }
        val result = DesignTokenValueResolver(index).resolve(variant).result

        assertTrue(result is DesignTokenValueResolution.Resolved)

        result as DesignTokenValueResolution.Resolved

        assertFalse(result.value.contains("var(", ignoreCase = true))
        assertNotNull(result.color)
        assertEquals("--tui-const-white", result.references.single().name)
        assertEquals(
            "--tui-const-white",
            requireNotNull(result.references.single().selectedVariant).name,
        )
    }

    private companion object {
        val LIGHT_DESKTOP =
            DesignTokenContext(
                platform = DesignTokenPlatform.DESKTOP,
                theme = DesignTokenTheme.LIGHT,
            )
    }
}
