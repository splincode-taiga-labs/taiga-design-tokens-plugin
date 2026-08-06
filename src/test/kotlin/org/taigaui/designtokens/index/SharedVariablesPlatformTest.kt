package org.taigaui.designtokens.index

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class SharedVariablesPlatformTest {
    @Test
    fun `classifies proprietary root variables less declarations for desktop and mobile`() {
        val declaration = sharedDeclaration()

        assertEquals(
            listOf(
                DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.UNSPECIFIED),
                DesignTokenContext(DesignTokenPlatform.MOBILE, DesignTokenTheme.UNSPECIFIED),
            ),
            DesignTokenContextClassifier().classifyAll(PACKAGE_ROOT, declaration),
        )
    }

    @Test
    fun `indexes proprietary shared variables for all platforms`() {
        val variants =
            DesignTokenIndex
                .build(
                    packageRoot = PACKAGE_ROOT,
                    declarations = listOf(sharedDeclaration()),
                ).find(TOKEN)

        assertEquals(
            setOf(DesignTokenPlatform.DESKTOP, DesignTokenPlatform.MOBILE),
            variants.map { variant -> variant.context.platform }.toSet(),
        )
    }

    @Test
    fun `keeps an ordinary proprietary less file desktop only`() {
        val declaration =
            sharedDeclaration().copy(
                sourceFile = PACKAGE_ROOT.resolve("styles/theme.less"),
            )

        assertEquals(
            listOf(DesignTokenContext.DEFAULT),
            DesignTokenContextClassifier().classifyAll(PACKAGE_ROOT, declaration),
        )
    }

    @Test
    fun `keeps another package variables less desktop only`() {
        val declaration = sharedDeclaration().copy(packageName = "@taiga-ui/core")

        assertEquals(
            listOf(DesignTokenContext.DEFAULT),
            DesignTokenContextClassifier().classifyAll(PACKAGE_ROOT, declaration),
        )
    }

    private fun sharedDeclaration(): DesignTokenDeclaration =
        DesignTokenDeclaration(
            name = TOKEN,
            value = "var(--tui-font-body-s)",
            sourceFile = PACKAGE_ROOT.resolve("styles/variables.less"),
            line = 5,
            selectorChain = listOf("&:root, :host"),
            packageName = PROPRIETARY_PACKAGE,
        )

    private companion object {
        const val TOKEN = "--tui-font-text-s"
        const val PROPRIETARY_PACKAGE = "@taiga-ui/proprietary"
        val PACKAGE_ROOT: Path =
            Path
                .of("build", "fixtures", "proprietary")
                .toAbsolutePath()
                .normalize()
    }
}
