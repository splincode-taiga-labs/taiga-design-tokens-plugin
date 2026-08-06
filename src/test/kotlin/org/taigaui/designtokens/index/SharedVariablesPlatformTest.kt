package org.taigaui.designtokens.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class SharedVariablesPlatformTest {
    private val classifier = DesignTokenContextClassifier()

    @Test
    fun `keeps root variables in the desktop declaration context`() {
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
    fun `recognizes root variables in any Taiga UI style package`() {
        val declaration = sharedDeclaration().copy(packageName = "@taiga-ui/core")

        assertTrue(classifier.isSharedAcrossPlatforms(PACKAGE_ROOT, declaration))
    }

    @Test
    fun `does not treat mobile selectors as shared fallbacks`() {
        val declaration =
            sharedDeclaration().copy(
                sourceFile = PACKAGE_ROOT.resolve("styles/tbank-theme-mobile.less"),
                selectorChain =
                    listOf(
                        "[data-platform='ios'], [data-platform='android']",
                    ),
            )

        assertEquals(
            DesignTokenPlatform.MOBILE,
            classifier.classify(PACKAGE_ROOT, declaration).platform,
        )
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
