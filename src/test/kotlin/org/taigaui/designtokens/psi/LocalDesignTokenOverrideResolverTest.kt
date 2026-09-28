package org.taigaui.designtokens.psi

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.index.DesignTokenDeclaration
import java.nio.file.Path

class LocalDesignTokenOverrideResolverTest : BasePlatformTestCase() {
    private lateinit var resolver: LocalDesignTokenOverrideResolver
    private lateinit var sourceFile: Path

    override fun setUp() {
        super.setUp()
        resolver = LocalDesignTokenOverrideResolver(project)
        sourceFile =
            Path
                .of("build", "psi-fixtures", "local-overrides", "component.less")
                .toAbsolutePath()
                .normalize()
    }

    fun testResolvesOverrideFromSameSelector() {
        val declaration =
            resolveDuration(
                """
                .scroll {
                    --tui-duration: 0;
                }

                .scroll {
                    transition: var(--tui-duration);
                }
                """.trimIndent(),
            )

        assertEquals("0", declaration?.value)
        assertEquals(listOf(".scroll"), declaration?.selectorChain)
        assertTrue(declaration?.localOverride == true)
    }

    fun testIgnoresOverrideFromDifferentSelector() {
        val declaration =
            resolveDuration(
                """
                .scroll {
                    --tui-duration: 0;
                }

                .other {
                    transition: var(--tui-duration);
                }
                """.trimIndent(),
            )

        assertNull(declaration)
    }

    fun testPrefersLaterOverrideFromSameSelector() {
        val declaration =
            resolveDuration(
                """
                .scroll {
                    --tui-duration: 150ms;
                }

                .scroll {
                    transition: var(--tui-duration);
                }

                .scroll {
                    --tui-duration: 0;
                }
                """.trimIndent(),
            )

        assertEquals("0", declaration?.value)
        assertEquals(10, declaration?.line)
    }

    fun testResolvesOverrideFromNestedLessParent() {
        val declaration =
            resolveDuration(
                """
                .parent {
                    --tui-duration: 0;

                    .child {
                        transition: var(--tui-duration);
                    }
                }
                """.trimIndent(),
            )

        assertEquals("0", declaration?.value)
        assertEquals(listOf(".parent"), declaration?.selectorChain)
    }

    private fun resolveDuration(content: String): DesignTokenDeclaration? {
        val psiFile = createPsiFile(content)
        val referenceOffset = content.indexOf("var(--tui-duration)") + "var(".length

        return resolver
            .resolve(
                psiFile = psiFile,
                sourceFile = sourceFile,
                referenceOffset = referenceOffset,
            ).singleOrNull { declaration -> declaration.name == TOKEN }
    }

    private fun createPsiFile(content: String): PsiFile {
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName("component.less")

        return PsiFileFactory.getInstance(project).createFileFromText(
            "component.less",
            fileType,
            content,
        )
    }

    private companion object {
        const val TOKEN = "--tui-duration"
    }
}
