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
    fun `defaults files without mobile marker to desktop`() {
        assertContext(
            relativePath = "palette/light.css",
            platform = DesignTokenPlatform.DESKTOP,
            theme = DesignTokenTheme.LIGHT,
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
            relativePath = "palette/less/dark.less",
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
    fun `classification is case insensitive`() {
        assertContext(
            relativePath = "Palette/MOBILE/DARK.CSS",
            platform = DesignTokenPlatform.MOBILE,
            theme = DesignTokenTheme.DARK,
        )
    }

    @Test
    fun `does not classify marker substrings as mobile`() {
        assertContext(
            relativePath = "palette/mobilestyle.css",
            platform = DesignTokenPlatform.DESKTOP,
        )
    }

    @Test
    fun `mobile marker takes precedence over desktop marker`() {
        assertContext(
            relativePath = "mobile/desktop.css",
            platform = DesignTokenPlatform.MOBILE,
        )
    }

    @Test
    fun `returns unspecified theme for conflicting theme markers`() {
        assertContext(
            relativePath = "light/dark.css",
            platform = DesignTokenPlatform.DESKTOP,
        )
    }

    @Test
    fun `defaults source outside package to desktop`() {
        val context = classifier.classify(
            packageRoot = packageRoot,
            sourceFile = packageRoot.parent.resolve("dark.css"),
        )

        assertEquals(
            DesignTokenContext(
                platform = DesignTokenPlatform.DESKTOP,
                theme = DesignTokenTheme.UNSPECIFIED,
            ),
            context,
        )
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
        platform: DesignTokenPlatform = DesignTokenPlatform.DESKTOP,
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
