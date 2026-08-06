package org.taigaui.designtokens.packageinfo

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.documentation.DesignTokenHoverPopupModel
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokensPackageScanner
import org.taigaui.designtokens.psi.PsiDesignTokenSourceExtractor
import org.taigaui.designtokens.resolution.DesignTokenValueResolver
import java.nio.file.Files
import java.nio.file.Path

class ProprietaryPlatformResolutionIntegrationTest : BasePlatformTestCase() {
    private lateinit var projectRoot: Path

    override fun setUp() {
        super.setUp()
        projectRoot = Files.createTempDirectory("taiga-ui-proprietary-platform")
        Files.writeString(projectRoot.resolve("app.less"), ".host { font: var(--tui-font-text-s); }")
    }

    override fun tearDown() {
        try {
            projectRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testResolvesDesktopAndMobileValuesBeforeCollapsingContexts() {
        writePackage(
            directory = "design-tokens",
            name = "@taiga-ui/design-tokens",
            files =
                mapOf(
                    "fonts/desktop.css" to
                        ":root { --tui-font-body-s: desktop-font; }",
                    "fonts/mobile.css" to
                        """
                        [data-platform='ios'],
                        [data-platform='android'] {
                            --tui-font-body-s: mobile-font;
                        }
                        """.trimIndent(),
                ),
        )
        writePackage(
            directory = "proprietary",
            name = "@taiga-ui/proprietary",
            files =
                mapOf(
                    "styles/theme/variables.less" to
                        """
                        &:root,
                        :host {
                            --tui-font-text-s: var(--tui-font-body-s);
                        }
                        """.trimIndent(),
                    "styles/tbank-theme-mobile.less" to
                        """
                        [data-platform='ios'],
                        [data-platform='android'] {
                            --tui-font-text-s: var(--tui-font-body-s);
                        }
                        """.trimIndent(),
                    "styles/theme/overrides.less" to
                        """
                        tui-rating {
                            --tui-font-text-s: local-component-value;
                        }
                        """.trimIndent(),
                ),
        )

        val packageSet =
            requireNotNull(
                DesignTokensPackageResolver().resolve(projectRoot.resolve("app.less")),
            )
        val scanner = DesignTokensPackageScanner(PsiDesignTokenSourceExtractor(project))
        val index =
            DesignTokenIndex.build(
                packageRoot = packageSet.realRoot,
                declarations = scanner.scan(packageSet),
            )
        val model =
            DesignTokenHoverPopupModel.create(
                tokenName = TOKEN,
                groups = DesignTokenValueResolver(index).resolveGrouped(TOKEN),
            )
        val section = model.sections.single()
        val appliedRows = section.rows.filter { row -> row.overrideMessage == null }
        val overriddenRows = section.rows.filter { row -> row.overrideMessage != null }

        assertEquals(
            setOf(
                "🖥️ Desktop · Any theme" to "desktop-font",
                "📱 Mobile · Any theme" to "mobile-font",
            ),
            appliedRows.map { row -> row.platform to row.resolvedValue }.toSet(),
        )
        assertEquals(1, overriddenRows.size)
        assertEquals("📱 Mobile · Any theme", overriddenRows.single().platform)
        assertEquals(
            "Overridden by a platform-specific declaration",
            overriddenRows.single().overrideMessage,
        )
        assertTrue(index.find(TOKEN).none { variant -> variant.rawValue == "local-component-value" })
    }

    private fun writePackage(
        directory: String,
        name: String,
        files: Map<String, String>,
    ) {
        val packageRoot = projectRoot.resolve("node_modules/@taiga-ui/$directory")

        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("package.json"),
            """
            {
                "name": "$name",
                "version": "4.69.0",
                "exports": {
                    "./styles/*": "./styles/*"
                }
            }
            """.trimIndent(),
        )

        files.forEach { (relativePath, content) ->
            val file = packageRoot.resolve(relativePath)

            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
    }

    private companion object {
        const val TOKEN = "--tui-font-text-s"
    }
}
