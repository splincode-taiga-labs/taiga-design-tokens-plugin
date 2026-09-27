package org.taigaui.designtokens.packageinfo

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.documentation.DesignTokenHoverPopupModel
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokensPackageScanner
import org.taigaui.designtokens.psi.PsiDesignTokenSourceExtractor
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import org.taigaui.designtokens.resolution.DesignTokenValueResolver
import java.nio.file.Files
import java.nio.file.Path

class TaigaUiStylePackagesIntegrationTest : BasePlatformTestCase() {
    private lateinit var projectRoot: Path

    override fun setUp() {
        super.setUp()
        projectRoot = Files.createTempDirectory("taiga-ui-style-packages")
        Files.writeString(projectRoot.resolve("app.less"), ".host { color: var(--tui-test); }")
    }

    override fun tearDown() {
        try {
            projectRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testResolvesCrossPackageVariablesFromTaigaUi4Layout() {
        writePackage(
            directory = "design-tokens",
            name = "@taiga-ui/design-tokens",
            version = "0.248.0",
            exports = null,
            files =
                mapOf(
                    "variables.css" to
                        ":root { --tui-v4-base: #123456; }",
                ),
        )
        writePackage(
            directory = "core",
            name = "@taiga-ui/core",
            version = "4.93.0",
            exports = "\"./styles/*\": \"./styles/*\"",
            files =
                mapOf(
                    "styles/variables.less" to
                        """
                        @import '@taiga-ui/design-tokens/variables.css';
                        :root { --tui-v4-core: var(--tui-v4-base); }
                        """.trimIndent(),
                ),
        )
        writePackage(
            directory = "proprietary",
            name = "@taiga-ui/proprietary",
            version = "4.93.0",
            exports = "\"./styles/*\": \"./styles/*\"",
            files =
                mapOf(
                    "styles/brand/unexpected-file-name.scss" to
                        """
                        @import '@taiga-ui/core/styles/variables.less';
                        :root { --tui-v4-proprietary: var(--tui-v4-core); }
                        """.trimIndent(),
                ),
        )

        val packageSet = resolvePackageSet()
        val index = buildIndex(packageSet)
        val result =
            DesignTokenValueResolver(index)
                .resolveGrouped("--tui-v4-proprietary")
                .single()
                .representative

        assertEquals(
            setOf(
                "@taiga-ui/core",
                "@taiga-ui/design-tokens",
                "@taiga-ui/proprietary",
            ),
            packageSet.sourcePackages
                .map { sourcePackage -> sourcePackage.name }
                .toSet(),
        )
        assertResolvedValue("#123456", result)
        assertEquals(
            setOf("@taiga-ui/proprietary"),
            index
                .find("--tui-v4-proprietary")
                .flatMap { variant -> variant.origins }
                .mapNotNull { origin -> origin.packageName }
                .toSet(),
        )
    }

    fun testTreatsTaigaUi4ProprietaryVariablesAsAllPlatforms() {
        val fontValue = "normal 0.875rem/1.25rem Arial, sans-serif"

        writePackage(
            directory = "design-tokens",
            name = "@taiga-ui/design-tokens",
            version = "0.248.0",
            exports = null,
            files =
                mapOf(
                    "fonts/desktop.css" to
                        ":root { --tui-font-body-s: $fontValue; }",
                ),
        )
        writePackage(
            directory = "proprietary",
            name = "@taiga-ui/proprietary",
            version = "4.93.0",
            exports = "\"./styles/*\": \"./styles/*\"",
            files =
                mapOf(
                    "styles/variables.less" to
                        """
                        @import (inline, once) '@taiga-ui/design-tokens/fonts/desktop.css';
                        @import (inline, once) '@taiga-ui/design-tokens/palette/gradient.css';
                        @import (inline, once) '@taiga-ui/design-tokens/palette/animation.css';
                        @import (inline, once) '@taiga-ui/design-tokens/palette/shadow.css';

                        &:root,
                        :host {
                            --tui-font-text-s: var(--tui-font-body-s);
                        }
                        """.trimIndent(),
                ),
        )

        val packageSet = resolvePackageSet()
        val index = buildIndex(packageSet)
        val groups = DesignTokenValueResolver(index).resolveGrouped("--tui-font-text-s")
        val variant =
            index
                .find("--tui-font-text-s")
                .single()
        val model = DesignTokenHoverPopupModel.create("--tui-font-text-s", groups)

        assertEquals(1, groups.size)
        assertResolvedValue(fontValue, groups.single().representative)
        assertTrue(
            variant.origins
                .single()
                .sharedAcrossPlatforms,
        )
        assertEquals(
            "All platforms · Any theme",
            model.sections
                .single()
                .rows
                .single()
                .platform,
        )
        assertEquals(
            setOf("@taiga-ui/proprietary"),
            groups
                .single()
                .origins
                .mapNotNull { origin -> origin.packageName }
                .toSet(),
        )
    }

    fun testDoesNotExposeCoreVariablesThatProprietaryThemeDoesNotImport() {
        writePackage(
            directory = "design-tokens",
            name = "@taiga-ui/design-tokens",
            version = "0.277.0",
            exports = null,
            files =
                mapOf(
                    "palette/text.css" to
                        ":root { --tui-text-primary: #000000cc; }",
                ),
        )
        writePackage(
            directory = "core",
            name = "@taiga-ui/core",
            version = "4.69.0",
            exports = "\"./styles/*\": \"./styles/*\"",
            files =
                mapOf(
                    "styles/theme/appearance.less" to
                        ".host { color: var(--tui-text-primary); }",
                    "styles/theme/variables.less" to
                        ":root { --tui-text-primary: rgba(27, 31, 59, 1); }",
                ),
        )
        writePackage(
            directory = "proprietary",
            name = "@taiga-ui/proprietary",
            version = "4.69.0",
            exports = "\"./styles/*\": \"./styles/*\"",
            files =
                mapOf(
                    "styles/tbank-theme.less" to
                        """
                        @import '@taiga-ui/design-tokens/palette/text.css';
                        @import '@taiga-ui/core/styles/theme/appearance.less';
                        """.trimIndent(),
                ),
        )

        val index = buildIndex(resolvePackageSet())
        val packages =
            index
                .find("--tui-text-primary")
                .flatMap { variant -> variant.origins }
                .mapNotNull { origin -> origin.packageName }
                .toSet()
        val result =
            DesignTokenValueResolver(index)
                .resolveGrouped("--tui-text-primary")
                .single()
                .representative

        assertEquals(setOf("@taiga-ui/design-tokens"), packages)
        assertResolvedValue("#000000cc", result)
    }

    fun testResolvesTaigaUi5StylesVariablesNotImportedByProprietaryTheme() {
        writePackage(
            directory = "design-tokens",
            name = "@taiga-ui/design-tokens",
            version = "0.312.0",
            exports = null,
            files =
                mapOf(
                    "palette.css" to
                        ":root { --tui-v5-base: #123456; }",
                ),
        )
        writePackage(
            directory = "styles",
            name = "@taiga-ui/styles",
            version = "5.18.0",
            exports = "\"./*\": \"./*\"",
            files =
                mapOf(
                    "mixins/theme/variables.less" to
                        """
                        .tui-theme-variables() {
                            --tui-duration: 0.3s;
                        }
                        """.trimIndent(),
                ),
        )
        writePackage(
            directory = "proprietary",
            name = "@taiga-ui/proprietary",
            version = "5.18.0",
            exports = "\"./styles/*\": \"./styles/*\"",
            files =
                mapOf(
                    "styles/theme/private-tokens.less" to
                        ":root { --tui-v5-proprietary: red; }",
                ),
        )

        val index = buildIndex(resolvePackageSet())
        val groups = DesignTokenValueResolver(index).resolveGrouped("--tui-duration")
        val packages =
            index
                .find("--tui-duration")
                .flatMap { variant -> variant.origins }
                .mapNotNull { origin -> origin.packageName }
                .toSet()

        assertEquals(setOf("@taiga-ui/styles"), packages)
        assertResolvedValue("0.3s", groups.single().representative)
    }

    fun testResolvesTaigaUi5StylesPackageAndIgnoresUnexportedCoreStyles() {
        writePackage(
            directory = "design-tokens",
            name = "@taiga-ui/design-tokens",
            version = "0.312.0",
            exports = null,
            files =
                mapOf(
                    "palette.css" to
                        ":root { --tui-v5-base: rgb(10, 20, 30); }",
                ),
        )
        writePackage(
            directory = "styles",
            name = "@taiga-ui/styles",
            version = "5.18.0",
            exports = "\"./*\": \"./*\"",
            files =
                mapOf(
                    "theme/variables.less" to
                        """
                        @import '@taiga-ui/design-tokens/palette.css';
                        :root { --tui-v5-surface: var(--tui-v5-base); }
                        """.trimIndent(),
                ),
        )
        writePackage(
            directory = "core",
            name = "@taiga-ui/core",
            version = "5.18.0",
            exports = null,
            files =
                mapOf(
                    "styles/internal.less" to
                        ":root { --tui-v5-ignored-core: hotpink; }",
                ),
        )
        writePackage(
            directory = "proprietary",
            name = "@taiga-ui/proprietary",
            version = "5.18.0",
            exports = "\"./styles/*\": \"./styles/*\"",
            files =
                mapOf(
                    "styles/theme/private-tokens.css" to
                        """
                        @import '@taiga-ui/styles/theme/variables.less';
                        :root { --tui-v5-proprietary: var(--tui-v5-surface); }
                        """.trimIndent(),
                ),
        )

        val packageSet = resolvePackageSet()
        val index = buildIndex(packageSet)
        val result =
            DesignTokenValueResolver(index)
                .resolveGrouped("--tui-v5-proprietary")
                .single()
                .representative

        assertEquals(
            setOf(
                "@taiga-ui/design-tokens",
                "@taiga-ui/proprietary",
                "@taiga-ui/styles",
            ),
            packageSet.sourcePackages
                .map { sourcePackage -> sourcePackage.name }
                .toSet(),
        )
        assertTrue(index.find("--tui-v5-ignored-core").isEmpty())
        assertResolvedValue("rgb(10, 20, 30)", result)
    }

    private fun resolvePackageSet(): DesignTokensPackage =
        requireNotNull(
            DesignTokensPackageResolver().resolve(projectRoot.resolve("app.less")),
        )

    private fun buildIndex(packageSet: DesignTokensPackage): DesignTokenIndex {
        val scanner =
            DesignTokensPackageScanner(
                sourceExtractor = PsiDesignTokenSourceExtractor(project),
            )

        return DesignTokenIndex.build(
            packageRoot = packageSet.realRoot,
            declarations = scanner.scan(packageSet),
        )
    }

    private fun assertResolvedValue(
        expected: String,
        result: DesignTokenValueResolution,
    ) {
        assertTrue(result is DesignTokenValueResolution.Resolved)
        assertEquals(expected, (result as DesignTokenValueResolution.Resolved).value)
    }

    private fun writePackage(
        directory: String,
        name: String,
        version: String,
        exports: String?,
        files: Map<String, String>,
    ) {
        val packageRoot = projectRoot.resolve("node_modules/@taiga-ui/$directory")
        val exportsBlock = exports?.let { value -> ",\n    \"exports\": { $value }" }.orEmpty()

        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("package.json"),
            """
            {
                "name": "$name",
                "version": "$version"$exportsBlock
            }
            """.trimIndent(),
        )

        files.forEach { (relativePath, content) ->
            val file = packageRoot.resolve(relativePath)

            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
    }
}
