package org.taigaui.designtokens.packageindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.taigaui.designtokens.tokenindex.DesignTokenContext
import org.taigaui.designtokens.tokenindex.DesignTokenDeclaration
import org.taigaui.designtokens.tokenindex.DesignTokenIndex
import org.taigaui.designtokens.tokenindex.DesignTokenPlatform
import org.taigaui.designtokens.tokenindex.DesignTokenSourceFormat
import org.taigaui.designtokens.tokenindex.DesignTokenTheme
import org.taigaui.designtokens.tokenindex.DesignTokensPackageScanner
import java.nio.file.Files
import java.nio.file.Path

class DesignTokensNpmPackageIntegrationTest {
    private val projectRoot = Path.of("").toAbsolutePath().normalize()
    private val packageJson = projectRoot.resolve(
        "node_modules/@taiga-ui/design-tokens/package.json",
    )

    @Test
    fun `resolves package installed from npm`() {
        val result = resolveInstalledPackage()
        val expectedRoot = packageJson.parent.toAbsolutePath().normalize()

        assertEquals(expectedRoot, result.root)
        assertEquals(expectedRoot.toRealPath(), result.realRoot)
        assertEquals(DESIGN_TOKENS_VERSION, result.version)
    }

    @Test
    fun `real package contains the pinned token source layout`() {
        val packageInfo = resolveInstalledPackage()

        val actualFiles = Files.walk(packageInfo.realRoot).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .map(packageInfo.realRoot::relativize)
                .map(Path::toString)
                .filter(::isSupportedTokenSource)
                .sorted()
                .toList()
        }

        assertEquals(EXPECTED_TOKEN_SOURCE_FILES, actualFiles)
    }

    @Test
    fun `scans declarations from package installed from npm`() {
        val designTokensPackage = resolveInstalledPackage()

        val declarations = DesignTokensPackageScanner().scan(designTokensPackage)

        assertTrue(
            "Expected the real npm package to contain Taiga UI custom-property declarations.",
            declarations.isNotEmpty(),
        )
        assertTrue(declarations.all { it.name.startsWith("--tui-") })
        assertTrue(declarations.all { it.sourceFile.startsWith(designTokensPackage.realRoot) })
    }

    @Test
    fun `turns real CSS Less and SCSS declarations into models`() {
        val designTokensPackage = resolveInstalledPackage()

        val declarations = DesignTokensPackageScanner().scan(designTokensPackage)
        val expectedDeclarations = listOf(
            DesignTokenDeclaration(
                name = "--tui-font-offset",
                value = "0rem",
                sourceFile = designTokensPackage.realRoot.resolve("fonts/desktop.css"),
                line = 3,
            ),
            DesignTokenDeclaration(
                name = "--tui-background-base",
                value = "var(--tui-const-white)",
                sourceFile = designTokensPackage.realRoot.resolve("angular/desktop.less"),
                line = 5,
            ),
            DesignTokenDeclaration(
                name = "--tui-background-base",
                value = "var(--tui-const-black-lighter-13)",
                sourceFile = designTokensPackage.realRoot.resolve("palette/scss/dark.scss"),
                line = 2,
            ),
        )

        expectedDeclarations.forEach { expected ->
            assertTrue(
                "Expected to parse the real declaration $expected",
                expected in declarations,
            )
        }
    }

    @Test
    fun `real package exposes CSS Less and SCSS declaration origins`() {
        val packageInfo = resolveInstalledPackage()
        val declarations = DesignTokensPackageScanner().scan(packageInfo)

        assertTrue(
            declarations
                .map { DesignTokenSourceFormat.from(it.sourceFile) }
                .containsAll(
                    listOf(
                        DesignTokenSourceFormat.CSS,
                        DesignTokenSourceFormat.LESS,
                        DesignTokenSourceFormat.SCSS,
                    ),
                ),
        )
    }

    @Test
    fun `builds logical index without losing real declarations`() {
        val packageInfo = resolveInstalledPackage()
        val declarations = DesignTokensPackageScanner().scan(packageInfo)

        val index = DesignTokenIndex.build(packageInfo.realRoot, declarations)

        assertEquals(
            "Every scanned declaration must remain represented by one physical origin.",
            declarations.size,
            index.originCount,
        )
        assertTrue(index.names.isNotEmpty())
        assertTrue(index.variants.isNotEmpty())
    }

    @Test
    fun `real package contains exact duplicates that collapse into logical variants`() {
        val packageInfo = resolveInstalledPackage()
        val declarations = DesignTokensPackageScanner().scan(packageInfo)

        val index = DesignTokenIndex.build(packageInfo.realRoot, declarations)

        assertTrue(
            "Expected parallel source representations to reduce the logical variant count.",
            index.variants.size < declarations.size,
        )
        assertTrue(
            "Expected at least one logical variant to retain multiple source origins.",
            index.variants.any { it.origins.size > 1 },
        )
    }

    @Test
    fun `groups real dark CSS and SCSS declarations into one variant`() {
        val packageInfo = resolveInstalledPackage()
        val variant = realIndex(packageInfo)
            .find("--tui-background-base")
            .single { candidate ->
                candidate.context == DesignTokenContext(
                    platform = DesignTokenPlatform.UNSPECIFIED,
                    theme = DesignTokenTheme.DARK,
                ) && candidate.rawValue == "var(--tui-const-black-lighter-13)"
            }

        assertEquals(
            setOf(
                packageInfo.realRoot.resolve("palette/dark.css"),
                packageInfo.realRoot.resolve("palette/scss/dark.scss"),
            ),
            variant.origins.map { it.sourceFile }.toSet(),
        )
        assertEquals(
            setOf(DesignTokenSourceFormat.CSS, DesignTokenSourceFormat.SCSS),
            variant.origins.map { it.format }.toSet(),
        )
    }

    @Test
    fun `groups real light CSS and SCSS declarations into one variant`() {
        val packageInfo = resolveInstalledPackage()
        val variant = realIndex(packageInfo)
            .find("--tui-background-base")
            .single { candidate ->
                candidate.context == DesignTokenContext(
                    platform = DesignTokenPlatform.UNSPECIFIED,
                    theme = DesignTokenTheme.LIGHT,
                ) && candidate.rawValue == "var(--tui-const-white)"
            }

        assertEquals(
            setOf(
                packageInfo.realRoot.resolve("palette/light.css"),
                packageInfo.realRoot.resolve("palette/scss/light.scss"),
            ),
            variant.origins.map { it.sourceFile }.toSet(),
        )
    }

    @Test
    fun `groups real mobile CSS and SCSS declarations into one variant`() {
        val packageInfo = resolveInstalledPackage()
        val variant = realIndex(packageInfo)
            .find("--tui-background-base")
            .single { candidate ->
                candidate.context == DesignTokenContext(
                    platform = DesignTokenPlatform.MOBILE,
                    theme = DesignTokenTheme.LIGHT,
                ) && candidate.rawValue == "var(--tui-const-white)"
            }

        assertEquals(
            setOf(
                packageInfo.realRoot.resolve("palette/mobile/light.css"),
                packageInfo.realRoot.resolve("palette/scss/mobile/light.scss"),
            ),
            variant.origins.map { it.sourceFile }.toSet(),
        )
    }

    @Test
    fun `groups public and mixin desktop Less declarations into one variant`() {
        val packageInfo = resolveInstalledPackage()
        val variant = realIndex(packageInfo)
            .find("--tui-background-base")
            .single { candidate ->
                candidate.context == DesignTokenContext(
                    platform = DesignTokenPlatform.DESKTOP,
                    theme = DesignTokenTheme.UNSPECIFIED,
                ) && candidate.rawValue == "var(--tui-const-white)"
            }

        assertEquals(
            setOf(
                packageInfo.realRoot.resolve("angular/desktop.less"),
                packageInfo.realRoot.resolve("angular/mixins/desktop.less"),
            ),
            variant.origins.map { it.sourceFile }.toSet(),
        )
        assertTrue(variant.origins.all { it.format == DesignTokenSourceFormat.LESS })
    }

    @Test
    fun `groups public and mixin mobile Less declarations into one variant`() {
        val packageInfo = resolveInstalledPackage()
        val variant = realIndex(packageInfo)
            .find("--tui-background-base")
            .single { candidate ->
                candidate.context == DesignTokenContext(
                    platform = DesignTokenPlatform.MOBILE,
                    theme = DesignTokenTheme.UNSPECIFIED,
                ) && candidate.rawValue == "var(--tui-const-black)"
            }

        assertEquals(
            setOf(
                packageInfo.realRoot.resolve("angular/mobile.less"),
                packageInfo.realRoot.resolve("angular/mixins/mobile.less"),
            ),
            variant.origins.map { it.sourceFile }.toSet(),
        )
    }

    @Test
    fun `keeps real light and dark variants separate`() {
        val packageInfo = resolveInstalledPackage()
        val variants = realIndex(packageInfo).find("--tui-background-base")

        assertTrue(
            variants.any {
                it.context.theme == DesignTokenTheme.LIGHT &&
                    it.rawValue == "var(--tui-const-white)"
            },
        )
        assertTrue(
            variants.any {
                it.context.theme == DesignTokenTheme.DARK &&
                    it.rawValue == "var(--tui-const-black-lighter-13)"
            },
        )
    }

    @Test
    fun `keeps real desktop declaration separate from light theme duplicate`() {
        val packageInfo = resolveInstalledPackage()
        val variants = realIndex(packageInfo)
            .find("--tui-background-base")
            .filter { it.rawValue == "var(--tui-const-white)" }

        assertTrue(
            variants.any {
                it.context == DesignTokenContext(
                    platform = DesignTokenPlatform.DESKTOP,
                    theme = DesignTokenTheme.UNSPECIFIED,
                ) && it.origins.any { origin ->
                    origin.sourceFile == packageInfo.realRoot.resolve("angular/desktop.less")
                }
            },
        )
        assertTrue(
            variants.any {
                it.context == DesignTokenContext(
                    platform = DesignTokenPlatform.UNSPECIFIED,
                    theme = DesignTokenTheme.LIGHT,
                ) && it.origins.any { origin ->
                    origin.sourceFile == packageInfo.realRoot.resolve("palette/light.css")
                }
            },
        )
    }

    @Test
    fun `keeps real mobile light and mobile dark variants separate`() {
        val packageInfo = resolveInstalledPackage()
        val variants = realIndex(packageInfo).find("--tui-background-base")

        assertTrue(
            variants.any {
                it.context == DesignTokenContext(
                    platform = DesignTokenPlatform.MOBILE,
                    theme = DesignTokenTheme.LIGHT,
                ) && it.rawValue == "var(--tui-const-white)"
            },
        )
        assertTrue(
            variants.any {
                it.context == DesignTokenContext(
                    platform = DesignTokenPlatform.MOBILE,
                    theme = DesignTokenTheme.DARK,
                ) && it.rawValue == "var(--tui-const-black)"
            },
        )
    }

    @Test
    fun `classifies known real desktop and mobile files`() {
        val packageInfo = resolveInstalledPackage()
        val variants = realIndex(packageInfo).variants

        assertTrue(
            variants.any { variant ->
                variant.context.platform == DesignTokenPlatform.DESKTOP &&
                    variant.origins.any {
                        it.sourceFile == packageInfo.realRoot.resolve("fonts/desktop.css")
                    }
            },
        )
        assertTrue(
            variants.any { variant ->
                variant.context.platform == DesignTokenPlatform.MOBILE &&
                    variant.origins.any {
                        it.sourceFile == packageInfo.realRoot.resolve("angular/mobile.less")
                    }
            },
        )
    }

    @Test
    fun `real index contains no repeated physical origins`() {
        val packageInfo = resolveInstalledPackage()
        val origins = realIndex(packageInfo)
            .variants
            .flatMap { it.origins }

        assertEquals(origins.size, origins.toSet().size)
    }

    @Test
    fun `real index returns token names in stable order`() {
        val packageInfo = resolveInstalledPackage()
        val names = realIndex(packageInfo).names

        assertEquals(names.sorted(), names)
    }

    @Test
    fun `real index returns empty result for unknown token`() {
        val packageInfo = resolveInstalledPackage()

        assertTrue(
            realIndex(packageInfo)
                .find("--tui-token-that-does-not-exist")
                .isEmpty(),
        )
    }

    private fun realIndex(packageInfo: DesignTokensPackage): DesignTokenIndex =
        DesignTokenIndex.build(
            packageRoot = packageInfo.realRoot,
            declarations = DesignTokensPackageScanner().scan(packageInfo),
        )

    private fun resolveInstalledPackage(): DesignTokensPackage {
        assumeTrue(
            "Run `npm ci` to execute the real-package integration tests.",
            Files.isRegularFile(packageJson),
        )

        val result = DesignTokensPackageResolver().resolve(
            projectRoot.resolve("build.gradle.kts"),
        )

        assertNotNull(result)

        return result!!
    }

    private fun isSupportedTokenSource(path: String): Boolean =
        path.endsWith(".css") || path.endsWith(".less") || path.endsWith(".scss")

    private companion object {
        const val DESIGN_TOKENS_VERSION = "0.310.0"

        val EXPECTED_TOKEN_SOURCE_FILES = listOf(
            "angular/desktop.less",
            "angular/fonts/desktop.less",
            "angular/fonts/mobile.less",
            "angular/mixins/animation.less",
            "angular/mixins/desktop.less",
            "angular/mixins/fonts/desktop.less",
            "angular/mixins/fonts/mobile.less",
            "angular/mixins/gradient.less",
            "angular/mixins/mobile.less",
            "angular/mixins/shadow.less",
            "angular/mobile.less",
            "fonts/desktop.css",
            "fonts/mobile.css",
            "palette/animation.css",
            "palette/dark.css",
            "palette/gradient.css",
            "palette/less/animation.less",
            "palette/light.css",
            "palette/mobile/dark.css",
            "palette/mobile/light.css",
            "palette/scss/animation.scss",
            "palette/scss/dark.scss",
            "palette/scss/light.scss",
            "palette/scss/mobile/dark.scss",
            "palette/scss/mobile/light.scss",
            "palette/shadow.css",
        )
    }
}
