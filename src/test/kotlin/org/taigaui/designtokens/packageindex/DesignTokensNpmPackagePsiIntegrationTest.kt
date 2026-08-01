package org.taigaui.designtokens.packageindex

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.tokenindex.DesignTokenIndex
import org.taigaui.designtokens.tokenindex.DesignTokenPlatform
import org.taigaui.designtokens.tokenindex.DesignTokenSourceFormat
import org.taigaui.designtokens.tokenindex.DesignTokensPackageScanner
import org.taigaui.designtokens.tokenindex.PsiDesignTokenSourceExtractor

class DesignTokensNpmPackagePsiIntegrationTest : BasePlatformTestCase() {
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

    fun testScansRealCssScssAndLessFilesThroughPsi() {
        val declarations = scanner.scan(packageInfo)
        val formats =
            declarations
                .map { DesignTokenSourceFormat.from(it.sourceFile) }
                .toSet()

        assertFalse(declarations.isEmpty())
        assertTrue(declarations.all { it.name.startsWith("--tui-") })
        assertTrue(declarations.all { it.sourceFile.startsWith(packageInfo.realRoot) })
        assertEquals(
            setOf(
                DesignTokenSourceFormat.CSS,
                DesignTokenSourceFormat.SCSS,
                DesignTokenSourceFormat.LESS,
            ),
            formats,
        )
    }

    fun testRealRulesetDeclarationsPreserveParentSelectorChains() {
        val declarations = scanner.scan(packageInfo)
        val lightPaletteDeclarations =
            declarations.filter {
                it.sourceFile == packageInfo.realRoot.resolve("palette/light.css")
            }

        assertFalse(lightPaletteDeclarations.isEmpty())
        assertTrue(
            "Expected PSI extraction to retain selector context for real ruleset declarations.",
            lightPaletteDeclarations.all { it.selectorChain.isNotEmpty() },
        )
    }

    fun testParsesKnownRealDeclarationsThroughPsi() {
        val declarations = scanner.scan(packageInfo)

        assertTrue(
            declarations.any {
                it.name == "--tui-background-base" &&
                    it.value == "var(--tui-const-white)" &&
                    it.sourceFile == packageInfo.realRoot.resolve("palette/light.css")
            },
        )
        assertTrue(
            declarations.any {
                it.name == "--tui-background-base" &&
                    it.value == "var(--tui-const-black-lighter-13)" &&
                    it.sourceFile == packageInfo.realRoot.resolve("palette/scss/dark.scss")
            },
        )
        assertTrue(
            declarations.any {
                it.name == "--tui-background-base" &&
                    it.value == "var(--tui-const-white)" &&
                    it.sourceFile == packageInfo.realRoot.resolve("angular/desktop.less")
            },
        )
    }

    fun testRealIndexContainsDesktopAndMobileVariants() {
        val declarations = scanner.scan(packageInfo)
        val index = DesignTokenIndex.build(packageInfo.realRoot, declarations)
        val tokenWithBothPlatforms =
            index.names.firstOrNull { tokenName ->
                index
                    .find(tokenName)
                    .map { it.context.platform }
                    .toSet()
                    .containsAll(
                        setOf(
                            DesignTokenPlatform.DESKTOP,
                            DesignTokenPlatform.MOBILE,
                        ),
                    )
            }

        assertNotNull(
            "Expected at least one real token to expose desktop and mobile variants.",
            tokenWithBothPlatforms,
        )
    }

    fun testRealPsiIndexRetainsEveryPhysicalDeclarationAndKnownSelectorContext() {
        val declarations = scanner.scan(packageInfo)
        val index = DesignTokenIndex.build(packageInfo.realRoot, declarations)
        val origins = index.variants.flatMap { it.origins }

        assertEquals(declarations.size, index.originCount)
        assertTrue(origins.any { it.selectorChain.isNotEmpty() })
        assertTrue(
            origins.any {
                it.sourceFile == packageInfo.realRoot.resolve("palette/light.css") &&
                    it.selectorChain.isNotEmpty()
            },
        )
    }
}
