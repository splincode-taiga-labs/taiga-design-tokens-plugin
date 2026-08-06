package org.taigaui.designtokens.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

class GlobalDesignTokenContextIntegrationTest : BasePlatformTestCase() {
    fun testIgnoresComponentAndStateScopedOverrides() {
        val packageRoot = Files.createTempDirectory("taiga-ui-global-token-context")
        val sourceFile = packageRoot.resolve("variables.less")

        try {
            Files.writeString(
                sourceFile,
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

            val names =
                PsiDesignTokenSourceExtractor(project)
                    .extract(sourceFile)
                    .map { declaration -> declaration.name }
                    .toSet()

            assertEquals(setOf("--tui-global", "--tui-mobile"), names)
        } finally {
            packageRoot.toFile().deleteRecursively()
        }
    }
}
