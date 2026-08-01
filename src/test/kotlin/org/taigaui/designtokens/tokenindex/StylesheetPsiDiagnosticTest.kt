package org.taigaui.designtokens.tokenindex

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StylesheetPsiDiagnosticTest : BasePlatformTestCase() {
    fun testPrintsCssScssAndLessPsiTrees() {
        samples.forEach { (fileName, content) ->
            val file = myFixture.configureByText(fileName, content)

            println("PSI_TREE_BEGIN $fileName language=${file.language.id} type=${file.fileType.name}")
            dump(file)
            println("PSI_TREE_END $fileName")
        }
    }

    private fun dump(
        element: PsiElement,
        depth: Int = 0,
    ) {
        val elementType = element.node?.elementType?.toString().orEmpty()
        val text = element.text
            .replace("\n", "\\n")
            .take(MAX_TEXT_LENGTH)

        println(
            "${"  ".repeat(depth)}${element.javaClass.name} type=$elementType text=$text",
        )

        var child = element.firstChild

        while (child != null) {
            dump(child, depth + 1)
            child = child.nextSibling
        }
    }
}

private const val MAX_TEXT_LENGTH = 120

private val samples = listOf(
    "tokens.css" to
        """
        :root {
            --tui-desktop: #fff;
        }

        [tuiPlatform='android'],
        [tuiPlatform='ios'] {
            --tui-mobile: #000;
        }
        """.trimIndent(),
    "tokens.scss" to
        """
        :root {
            --tui-desktop: #fff;

            [tuiPlatform='android'] &,
            [tuiPlatform='ios'] & {
                --tui-mobile: #000;
            }
        }
        """.trimIndent(),
    "tokens.less" to
        """
        :root {
            --tui-desktop: #fff;

            [tuiPlatform='android'] &,
            [tuiPlatform='ios'] & {
                --tui-mobile: #000;
            }
        }
        """.trimIndent(),
)
