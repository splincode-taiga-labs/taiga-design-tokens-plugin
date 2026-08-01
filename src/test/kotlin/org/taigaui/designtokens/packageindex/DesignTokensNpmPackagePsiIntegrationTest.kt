package org.taigaui.designtokens.packageindex

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.tokenindex.DesignTokenContextClassifier
import org.taigaui.designtokens.tokenindex.DesignTokenIndex
import org.taigaui.designtokens.tokenindex.DesignTokenPlatform
import org.taigaui.designtokens.tokenindex.DesignTokenSourceFormat
import org.taigaui.designtokens.tokenindex.DesignTokensPackageScanner

class DesignTokensNpmPackagePsiIntegrationTest : BasePlatformTestCase() {
    private lateinit var fixture: InstalledDesignTokensPackageFixture
    private lateinit var packageInfo: DesignTokensPackage
    private lateinit var scanner: DesignTokensPackageScanner

    override fun setUp() {
        super.setUp()
        fixture = InstalledDesignTokensPackageFixture()
        packageInfo = fixture.resolve()
        scanner = DesignTokensPackageScanner(project)
    }

    fun testScansRealCssScssAndLessFilesThroughPsi() {
        val declarations = scanner.scan(packageInfo)
        val formats = declarations
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

    fun testRealDeclarationsPreserveParentSelectorChains() {
        val declarations = scanner.scan(packageInfo)

        assertTrue(
            "Expected PSI extraction to retain selector context for real token declarations.",
            declarations.any { it.selectorChain.isNotEmpty() },
        )
        assertTrue(
            "Every real custom property should belong to at least one ruleset.",
            declarations.all { it.selectorChain.isNotEmpty() },
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

    fun testFindsRealMobileSelectorsOutsideMobileDirectories() {
        val declarations = scanner.scan(packageInfo)
        val classifier = DesignTokenContextClassifier()
        val selectorMobileDeclarations = declarations.filter { declaration ->
            !packageInfo.realRoot
                .relativize(declaration.sourceFile)
                .any { it.toString().equals("mobile", ignoreCase = true) } &&
                declaration.selectorChain.any(::containsMobilePlatformSelector)
        }

        assertFalse(
            "Expected the pinned package to contain platform-specific selectors outside mobile directories.",
            selectorMobileDeclarations.isEmpty(),
        )
        assertTrue(
            selectorMobileDeclarations.all {
                classifier.classify(packageInfo.realRoot, it).platform == DesignTokenPlatform.MOBILE
            },
        )
    }

    fun testRealIndexContainsDesktopAndSelectorBasedMobileVariants() {
        val declarations = scanner.scan(packageInfo)
        val index = DesignTokenIndex.build(packageInfo.realRoot, declarations)
        val tokenWithBothPlatforms = index.names.firstOrNull { tokenName ->
            index.find(tokenName)
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

    fun testRealPsiIndexRetainsEveryPhysicalDeclaration() {
        val declarations = scanner.scan(packageInfo)
        val index = DesignTokenIndex.build(packageInfo.realRoot, declarations)

        assertEquals(declarations.size, index.originCount)
        assertTrue(
            index.variants
                .flatMap { it.origins }
                .all { it.selectorChain.isNotEmpty() },
        )
    }

    private fun containsMobilePlatformSelector(selector: String): Boolean {
        val hasPlatformAttribute = selector.contains("tuiPlatform", ignoreCase = true)
        val hasMobileValue =
            selector.contains("android", ignoreCase = true) ||
                selector.contains("ios", ignoreCase = true)

        return hasPlatformAttribute && hasMobileValue
    }
}
