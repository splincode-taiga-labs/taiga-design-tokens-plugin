package org.taigaui.designtokens.tokenindex

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class DesignTokenContextClassifierTest {
    private val packageRoot = Path.of("build", "fixtures", "design-tokens")
        .toAbsolutePath()
        .normalize()
    private val classifier = DesignTokenContextClassifier()

    @Test
    fun `defaults to desktop when path and selectors contain no mobile context`() {
        assertContext(
            relativePath = "palette/light.css",
            platform = DesignTokenPlatform.DESKTOP,
            theme = DesignTokenTheme.LIGHT,
        )
    }

    @Test
    fun `classifies desktop from file name`() {
        assertContext(
            relativePath = "fonts/desktop.css",
            platform = DesignTokenPlatform.DESKTOP,
        )
    }

    @Test
    fun `classifies mobile from file name`() {
        assertContext(
            relativePath = "angular/mobile.less",
            platform = DesignTokenPlatform.MOBILE,
        )
    }

    @Test
    fun `classifies mobile from directory name`() {
        assertContext(
            relativePath = "palette/mobile/light.css",
            platform = DesignTokenPlatform.MOBILE,
            theme = DesignTokenTheme.LIGHT,
        )
    }

    @Test
    fun `classifies light theme from file name`() {
        assertContext(
            relativePath = "palette/scss/light.scss",
            platform = DesignTokenPlatform.DESKTOP,
            theme = DesignTokenTheme.LIGHT,
        )
    }

    @Test
    fun `classifies dark theme from file name`() {
        assertContext(
            relativePath = "palette/scss/dark.scss",
            platform = DesignTokenPlatform.DESKTOP,
            theme = DesignTokenTheme.DARK,
        )
    }

    @Test
    fun `classifies combined mobile dark context`() {
        assertContext(
            relativePath = "palette/mobile/scss/dark.scss",
            platform = DesignTokenPlatform.MOBILE,
            theme = DesignTokenTheme.DARK,
        )
    }

    @Test
    fun `path classification is case insensitive`() {
        assertContext(
            relativePath = "Palette/MOBILE/DARK.CSS",
            platform = DesignTokenPlatform.MOBILE,
            theme = DesignTokenTheme.DARK,
        )
    }

    @Test
    fun `does not classify marker substrings`() {
        assertContext(
            relativePath = "palette/mobile-first-highlight.css",
            platform = DesignTokenPlatform.DESKTOP,
        )
    }

    @Test
    fun `mobile path marker takes precedence over desktop marker`() {
        assertContext(
            relativePath = "mobile/desktop.css",
            platform = DesignTokenPlatform.MOBILE,
        )
    }

    @Test
    fun `returns unspecified theme for conflicting markers`() {
        assertContext(
            relativePath = "light/dark.css",
            platform = DesignTokenPlatform.DESKTOP,
        )
    }

    @Test
    fun `defaults source outside package to desktop unspecified`() {
        val context = classifier.classify(
            packageRoot = packageRoot,
            declaration = declaration(
                sourceFile = packageRoot.parent.resolve("tokens.css"),
            ),
        )

        assertEquals(DesignTokenContext.DEFAULT, context)
    }

    @Test
    fun `normalizes package and source paths before classification`() {
        val context = classifier.classify(
            packageRoot = packageRoot.resolve("nested/.."),
            declaration = declaration(
                sourceFile = packageRoot.resolve("palette/../fonts/desktop.css"),
            ),
        )

        assertEquals(DesignTokenContext.DEFAULT, context)
    }

    @Test
    fun `classifies android selector as mobile`() {
        assertContext(
            relativePath = "palette/light.css",
            selectors = listOf(":root", "[tuiPlatform='android'] &"),
            platform = DesignTokenPlatform.MOBILE,
            theme = DesignTokenTheme.LIGHT,
        )
    }

    @Test
    fun `classifies ios selector as mobile`() {
        assertContext(
            relativePath = "palette/dark.css",
            selectors = listOf("[tuiPlatform=\"ios\"]"),
            platform = DesignTokenPlatform.MOBILE,
            theme = DesignTokenTheme.DARK,
        )
    }

    @Test
    fun `classifies mobile selector with whitespace and mixed case`() {
        assertContext(
            relativePath = "palette/light.css",
            selectors = listOf("[ TUIPlatform = 'Android' ] &"),
            platform = DesignTokenPlatform.MOBILE,
            theme = DesignTokenTheme.LIGHT,
        )
    }

    @Test
    fun `finds mobile selector in any parent level`() {
        assertContext(
            relativePath = "palette/light.css",
            selectors = listOf(
                ":root",
                ".theme",
                "[tuiPlatform='ios'] &",
                ".component",
            ),
            platform = DesignTokenPlatform.MOBILE,
            theme = DesignTokenTheme.LIGHT,
        )
    }

    @Test
    fun `keeps web platform selector desktop`() {
        assertContext(
            relativePath = "palette/light.css",
            selectors = listOf("[tuiPlatform='web']"),
            platform = DesignTokenPlatform.DESKTOP,
            theme = DesignTokenTheme.LIGHT,
        )
    }

    @Test
    fun `does not match similar attribute name`() {
        assertContext(
            relativePath = "palette/light.css",
            selectors = listOf("[notTuiPlatform='android']"),
            platform = DesignTokenPlatform.DESKTOP,
            theme = DesignTokenTheme.LIGHT,
        )
    }

    @Test
    fun `does not match extended platform value`() {
        assertContext(
            relativePath = "palette/light.css",
            selectors = listOf("[tuiPlatform='android-tablet']"),
            platform = DesignTokenPlatform.DESKTOP,
            theme = DesignTokenTheme.LIGHT,
        )
    }

    private fun assertContext(
        relativePath: String,
        selectors: List<String> = emptyList(),
        platform: DesignTokenPlatform,
        theme: DesignTokenTheme = DesignTokenTheme.UNSPECIFIED,
    ) {
        val context = classifier.classify(
            packageRoot = packageRoot,
            declaration = declaration(
                sourceFile = packageRoot.resolve(relativePath),
                selectors = selectors,
            ),
        )

        assertEquals(
            DesignTokenContext(platform = platform, theme = theme),
            context,
        )
    }

    private fun declaration(
        sourceFile: Path,
        selectors: List<String> = emptyList(),
    ): DesignTokenDeclaration = DesignTokenDeclaration(
        name = "--tui-test",
        value = "test",
        sourceFile = sourceFile,
        line = 1,
        selectorChain = selectors,
    )
}
