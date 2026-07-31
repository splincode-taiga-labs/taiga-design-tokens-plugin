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
    fun `classifies platform from directory name`() {
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
            theme = DesignTokenTheme.LIGHT,
        )
    }

    @Test
    fun `classifies dark theme from file name`() {
        assertContext(
            relativePath = "palette/less/dark.less",
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
    fun `classification is case insensitive`() {
        assertContext(
            relativePath = "Palette/MOBILE/DARK.CSS",
            platform = DesignTokenPlatform.MOBILE,
            theme = DesignTokenTheme.DARK,
        )
    }

    @Test
    fun `does not classify marker substrings`() {
        assertContext(
            relativePath = "palette/highlight.css",
        )
    }

    @Test
    fun `returns unspecified platform for conflicting markers`() {
        assertContext(
            relativePath = "mobile/desktop.css",
            theme = DesignTokenTheme.UNSPECIFIED,
        )
    }

    @Test
    fun `returns unspecified theme for conflicting markers`() {
        assertContext(
            relativePath = "light/dark.css",
        )
    }

    @Test
    fun `returns unspecified context for source outside package`() {
        val context = classifier.classify(
            packageRoot = packageRoot,
            sourceFile = packageRoot.parent.resolve("desktop/dark.css"),
        )

        assertEquals(DesignTokenContext.UNSPECIFIED, context)
    }

    @Test
    fun `normalizes package and source paths before classification`() {
        val context = classifier.classify(
            packageRoot = packageRoot.resolve("nested/.."),
            sourceFile = packageRoot.resolve("palette/../fonts/desktop.css"),
        )

        assertEquals(
            DesignTokenContext(
                platform = DesignTokenPlatform.DESKTOP,
                theme = DesignTokenTheme.UNSPECIFIED,
            ),
            context,
        )
    }

    private fun assertContext(
        relativePath: String,
        platform: DesignTokenPlatform = DesignTokenPlatform.UNSPECIFIED,
        theme: DesignTokenTheme = DesignTokenTheme.UNSPECIFIED,
    ) {
        val context = classifier.classify(
            packageRoot = packageRoot,
            sourceFile = packageRoot.resolve(relativePath),
        )

        assertEquals(
            DesignTokenContext(platform = platform, theme = theme),
            context,
        )
    }
}
