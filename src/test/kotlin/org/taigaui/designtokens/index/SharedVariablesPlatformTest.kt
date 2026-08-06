package org.taigaui.designtokens.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class SharedVariablesPlatformTest {
    private val classifier = DesignTokenContextClassifier()

    @Test
    fun `keeps proprietary root variables in the desktop resolution context`() {
        val declaration = sharedDeclaration()

        assertEquals(
            DesignTokenContext.DEFAULT,
            classifier.classify(PACKAGE_ROOT, declaration),
        )
        assertTrue(classifier.isSharedAcrossPlatforms(PACKAGE_ROOT, declaration))
    }

    @Test
    fun `retains shared platform metadata on the indexed origin`() {
        val variants =
            DesignTokenIndex
                .build(
                    packageRoot = PACKAGE_ROOT,
                    declarations = listOf(sharedDeclaration()),
                ).find(TOKEN)

        assertEquals(1, variants.size)
        assertEquals(DesignTokenContext.DEFAULT, variants.single().context)
        assertTrue(variants.single().origins.single().sharedAcrossPlatforms)
    }

    @Test
    fun `keeps an ordinary proprietary less file desktop only`() {
        val declaration =
            sharedDeclaration().copy(
                sourceFile = PACKAGE_ROOT.resolve("styles/theme.less"),
            )

        assertFalse(classifier.isSharedAcrossPlatforms(PACKAGE_ROOT, declaration))
    }

    @Test
    fun `keeps another package variables less desktop only`() {
        val declaration = sharedDeclaration().copy(packageName = "@taiga-ui/core")

        assertFalse(classifier.isSharedAcrossPlatforms(PACKAGE_ROOT, declaration))
    }

    private fun sharedDeclaration(): DesignTokenDeclaration =
        DesignTokenDeclaration(
            name = TOKEN,
            value = "var(--tui-font-body-s)",
            sourceFile = PACKAGE_ROOT.resolve("styles/variables.less"),
            line = 5,
            selectorChain = listOf("&:root, :host"),
            packageName = PROPRIETARY_PACKAGE,
            packageRoot = PACKAGE_ROOT,
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
