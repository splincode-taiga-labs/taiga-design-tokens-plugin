package org.taigaui.designtokens.psi

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

class PsiDesignTokenDeprecationTest : BasePlatformTestCase() {
    fun testExtractsLeadingDeprecationCommentAndReplacement() {
        val declaration =
            extract(
                """
                :root {
                    /** @deprecated use --tui-new instead */
                    --tui-old: #fff;
                    --tui-new: #fff;
                }
                """.trimIndent(),
            ).first { declaration -> declaration.name == "--tui-old" }

        assertEquals("use --tui-new instead", declaration.deprecation?.message)
        assertEquals("--tui-new", declaration.deprecation?.replacement)
    }

    fun testDoesNotLeakDeprecationToAdjacentDeclaration() {
        val declarations =
            extract(
                """
                :root {
                    /** @deprecated use --tui-new instead */
                    --tui-old: #fff;
                    --tui-new: #000;
                }
                """.trimIndent(),
            ).associateBy { declaration -> declaration.name }

        assertNotNull(declarations.getValue("--tui-old").deprecation)
        assertNull(declarations.getValue("--tui-new").deprecation)
    }

    private fun extract(content: String) =
        PsiDesignTokenSourceExtractor(project).extract(
            psiFile =
                PsiFileFactory
                    .getInstance(project)
                    .createFileFromText(
                        "tokens.css",
                        FileTypeManager.getInstance().getFileTypeByFileName("tokens.css"),
                        content,
                    ),
            sourceFile = Path.of("build", "deprecated-token-fixture", "tokens.css"),
        )
}
