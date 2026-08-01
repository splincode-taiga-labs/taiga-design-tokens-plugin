package org.taigaui.designtokens.tokenindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class DesignTokenIndexTest {
    private val packageRoot = Path.of("build", "fixtures", "design-tokens")
        .toAbsolutePath()
        .normalize()

    @Test
    fun `finds variants by token name`() {
        val index = buildIndex(
            declaration("palette/light.css", "--tui-background-base", "#fff", 2),
            declaration("palette/dark.css", "--tui-background-base", "#111", 2),
            declaration("palette/light.css", "--tui-text-primary", "#222", 3),
        )

        assertEquals(2, index.find("--tui-background-base").size)
        assertEquals(1, index.find("--tui-text-primary").size)
    }

    @Test
    fun `returns empty list for unknown token`() {
        val index = buildIndex(
            declaration("palette/light.css", "--tui-background-base", "#fff", 2),
        )

        assertTrue(index.find("--tui-unknown").isEmpty())
    }

    @Test
    fun `groups exact CSS Less and SCSS duplicates into one logical variant`() {
        val index = buildIndex(
            declaration("palette/dark.css", "--tui-background-base", "#111", 2),
            declaration("palette/less/dark.less", "--tui-background-base", "#111", 2),
            declaration("palette/scss/dark.scss", "--tui-background-base", "#111", 2),
        )

        val variant = index.find("--tui-background-base").single()

        assertEquals(
            DesignTokenContext(
                platform = DesignTokenPlatform.DESKTOP,
                theme = DesignTokenTheme.DARK,
            ),
            variant.context,
        )
        assertEquals("#111", variant.rawValue)
        assertEquals(
            listOf(
                DesignTokenSourceFormat.CSS,
                DesignTokenSourceFormat.LESS,
                DesignTokenSourceFormat.SCSS,
            ),
            variant.origins.map(DesignTokenOrigin::format),
        )
        assertEquals(
            listOf(
                packageRoot.resolve("palette/dark.css"),
                packageRoot.resolve("palette/less/dark.less"),
                packageRoot.resolve("palette/scss/dark.scss"),
            ),
            variant.origins.map(DesignTokenOrigin::sourceFile),
        )
    }

    @Test
    fun `does not merge declarations with different raw values`() {
        val index = buildIndex(
            declaration("palette/dark.css", "--tui-background-base", "#111", 2),
            declaration("palette/scss/dark.scss", "--tui-background-base", "var(--tui-black)", 2),
        )

        assertEquals(
            listOf("#111", "var(--tui-black)"),
            index.find("--tui-background-base").map(DesignTokenVariant::rawValue),
        )
    }

    @Test
    fun `does not merge declarations from different themes`() {
        val index = buildIndex(
            declaration("palette/light.css", "--tui-background-base", "#fff", 2),
            declaration("palette/dark.css", "--tui-background-base", "#fff", 2),
        )

        assertEquals(
            listOf(DesignTokenTheme.LIGHT, DesignTokenTheme.DARK),
            index.find("--tui-background-base").map { it.context.theme },
        )
    }

    @Test
    fun `does not merge declarations from different platforms`() {
        val index = buildIndex(
            declaration("fonts/desktop.css", "--tui-font-offset", "0rem", 3),
            declaration("fonts/mobile.css", "--tui-font-offset", "0rem", 3),
        )

        assertEquals(
            listOf(DesignTokenPlatform.DESKTOP, DesignTokenPlatform.MOBILE),
            index.find("--tui-font-offset").map { it.context.platform },
        )
    }

    @Test
    fun `defaults unmarked declarations to desktop and keeps mobile separate`() {
        val index = buildIndex(
            declaration("palette/animation.css", "--tui-duration", "300ms", 2),
            declaration("palette/mobile/animation.css", "--tui-duration", "300ms", 2),
        )

        assertEquals(
            listOf(DesignTokenPlatform.DESKTOP, DesignTokenPlatform.MOBILE),
            index.find("--tui-duration").map { it.context.platform },
        )
    }

    @Test
    fun `deduplicates repeated physical origin`() {
        val declaration = declaration(
            "palette/dark.css",
            "--tui-background-base",
            "#111",
            2,
        )
        val index = buildIndex(declaration, declaration)

        assertEquals(1, index.find("--tui-background-base").single().origins.size)
        assertEquals(1, index.originCount)
    }

    @Test
    fun `keeps declarations on different lines as separate origins`() {
        val index = buildIndex(
            declaration("palette/dark.css", "--tui-background-base", "#111", 2),
            declaration("palette/dark.css", "--tui-background-base", "#111", 20),
        )

        assertEquals(
            listOf(2, 20),
            index.find("--tui-background-base").single().origins.map(DesignTokenOrigin::line),
        )
    }

    @Test
    fun `normalizes source paths stored in origins`() {
        val declaration = DesignTokenDeclaration(
            name = "--tui-background-base",
            value = "#fff",
            sourceFile = packageRoot.resolve("palette/../palette/light.css"),
            line = 2,
        )

        val origin = buildIndex(declaration)
            .find("--tui-background-base")
            .single()
            .origins
            .single()

        assertEquals(packageRoot.resolve("palette/light.css"), origin.sourceFile)
    }

    @Test
    fun `preserves raw declaration value unchanged`() {
        val rawValue = "var(--tui-const-white, rgb(255 255 255 / 90%))"
        val index = buildIndex(
            declaration("palette/light.css", "--tui-background-base", rawValue, 2),
        )

        assertEquals(rawValue, index.find("--tui-background-base").single().rawValue)
    }

    @Test
    fun `retains unsupported source format as unknown`() {
        val index = buildIndex(
            declaration("palette/light.tokens", "--tui-background-base", "#fff", 2),
        )

        assertEquals(
            DesignTokenSourceFormat.UNKNOWN,
            index.find("--tui-background-base").single().origins.single().format,
        )
    }

    @Test
    fun `reports number of retained unique origins`() {
        val index = buildIndex(
            declaration("palette/light.css", "--tui-background-base", "#fff", 2),
            declaration("palette/scss/light.scss", "--tui-background-base", "#fff", 2),
            declaration("palette/dark.css", "--tui-background-base", "#111", 2),
            declaration("fonts/desktop.css", "--tui-font-offset", "0rem", 3),
        )

        assertEquals(4, index.originCount)
    }

    @Test
    fun `exposes token names in stable sorted order`() {
        val index = buildIndex(
            declaration("palette/light.css", "--tui-z", "1", 2),
            declaration("palette/light.css", "--tui-a", "2", 3),
            declaration("palette/light.css", "--tui-m", "3", 4),
        )

        assertEquals(listOf("--tui-a", "--tui-m", "--tui-z"), index.names)
    }

    @Test
    fun `build result is independent from declaration input order`() {
        val declarations = listOf(
            declaration("palette/light.css", "--tui-background-base", "#fff", 2),
            declaration("palette/scss/light.scss", "--tui-background-base", "#fff", 2),
            declaration("palette/dark.css", "--tui-background-base", "#111", 2),
            declaration("fonts/desktop.css", "--tui-font-offset", "0rem", 3),
        )

        val forward = DesignTokenIndex.build(packageRoot, declarations)
        val reversed = DesignTokenIndex.build(packageRoot, declarations.reversed())

        assertEquals(forward.names, reversed.names)
        assertEquals(forward.variants, reversed.variants)
    }

    @Test
    fun `orders physical origins by CSS Less SCSS and then unknown`() {
        val index = buildIndex(
            declaration("palette/dark.tokens", "--tui-background-base", "#111", 2),
            declaration("palette/scss/dark.scss", "--tui-background-base", "#111", 2),
            declaration("palette/less/dark.less", "--tui-background-base", "#111", 2),
            declaration("palette/dark.css", "--tui-background-base", "#111", 2),
        )

        assertEquals(
            listOf(
                DesignTokenSourceFormat.CSS,
                DesignTokenSourceFormat.LESS,
                DesignTokenSourceFormat.SCSS,
                DesignTokenSourceFormat.UNKNOWN,
            ),
            index.find("--tui-background-base").single().origins.map(DesignTokenOrigin::format),
        )
    }

    private fun buildIndex(vararg declarations: DesignTokenDeclaration): DesignTokenIndex =
        DesignTokenIndex.build(
            packageRoot = packageRoot,
            declarations = declarations.toList(),
        )

    private fun declaration(
        relativePath: String,
        name: String,
        value: String,
        line: Int,
    ): DesignTokenDeclaration = DesignTokenDeclaration(
        name = name,
        value = value,
        sourceFile = packageRoot.resolve(relativePath),
        line = line,
    )
}
