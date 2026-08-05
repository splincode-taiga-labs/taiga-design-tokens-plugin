package org.taigaui.designtokens.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import java.nio.file.Files

class LessThemeMixinContextIntegrationTest : BasePlatformTestCase() {
    fun testClassifiesVariablesInsideLightAndDarkMixins() {
        val packageRoot = Files.createTempDirectory("taiga-ui-less-theme")
        val sourceFile = packageRoot.resolve("variables.less")

        try {
            Files.writeString(
                sourceFile,
                """
                .dark() {
                    --tui-text-primary: rgba(255, 255, 255, 1);
                }

                .light() {
                    --tui-text-primary: rgba(27, 31, 59, 1);
                }
                """.trimIndent(),
            )

            val declarations = PsiDesignTokenSourceExtractor(project).extract(sourceFile)
            val variants = DesignTokenIndex.build(packageRoot, declarations).find("--tui-text-primary")

            assertEquals(
                setOf(
                    DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.DARK),
                    DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.LIGHT),
                ),
                variants.map { variant -> variant.context }.toSet(),
            )
            assertTrue(declarations.any { declaration -> ".dark()" in declaration.selectorChain })
            assertTrue(declarations.any { declaration -> ".light()" in declaration.selectorChain })
        } finally {
            packageRoot.toFile().deleteRecursively()
        }
    }
}
