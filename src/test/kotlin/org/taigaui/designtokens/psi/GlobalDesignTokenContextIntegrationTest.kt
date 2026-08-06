package org.taigaui.designtokens.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

class GlobalDesignTokenContextIntegrationTest : BasePlatformTestCase() {
    fun testIgnoresComponentAndStateScopedOverrides() {
        val names =
            extractNames(
                """
                &:root,
                :host {
                    --tui-global: red;
                }

                tui-rating {
                    --tui-local-rating: blue;
                }

                [data-platform='ios'],
                [data-platform='android'] {
                    --tui-mobile: green;

                    tui-textfield {
                        --tui-local-mobile: yellow;
                    }
                }
                """.trimIndent(),
            )

        assertEquals(setOf("--tui-global", "--tui-mobile"), names)
    }

    fun testKeepsCompositePlatformAndThemeSelectors() {
        val names =
            extractNames(
                """
                :root {
                    [tuiPlatform='ios'] & {
                        --tui-ios: red;
                    }
                }

                [data-platform='ios'][tuiTheme='dark'],
                [data-platform='android'] [tuiTheme='dark'],
                [tuiTheme='dark'] [data-platform='ios'] {
                    --tui-mobile-dark: black;
                }
                """.trimIndent(),
            )

        assertEquals(setOf("--tui-ios", "--tui-mobile-dark"), names)
    }

    private fun extractNames(content: String): Set<String> {
        val packageRoot = Files.createTempDirectory("taiga-ui-global-token-context")
        val sourceFile = packageRoot.resolve("variables.less")

        return try {
            Files.writeString(sourceFile, content)

            PsiDesignTokenSourceExtractor(project)
                .extract(sourceFile)
                .map { declaration -> declaration.name }
                .toSet()
        } finally {
            packageRoot.toFile().deleteRecursively()
        }
    }
}
