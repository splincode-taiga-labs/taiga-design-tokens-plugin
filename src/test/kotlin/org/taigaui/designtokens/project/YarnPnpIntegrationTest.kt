package org.taigaui.designtokens.project

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.icons.IconCompletionService
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import java.nio.file.Files
import java.nio.file.Path

class YarnPnpIntegrationTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var tokenService: DesignTokenIndexService
    private lateinit var iconService: IconCompletionService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("yarn-pnp-integration")
        tokenService = project.getService(DesignTokenIndexService::class.java)
        iconService = project.getService(IconCompletionService::class.java)
        tokenService.clear()
        iconService.clear()
    }

    override fun tearDown() {
        try {
            tokenService.clear()
            iconService.clear()
            tempRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testResolvesTokensAndIconsWithoutNodeModules() {
        val workspace = tempRoot.resolve("workspace")
        val sourceFile = createSourceFile(workspace)
        val designTokensRoot = workspace.resolve(".yarn/packages/design-tokens")
        val iconsRoot = workspace.resolve(".yarn/packages/icons")

        createDesignTokensPackage(
            root = designTokensRoot,
            version = "0.322.0",
            tokenValue = "#123456",
        )
        createIconsPackage(
            root = iconsRoot,
            version = "5.25.0",
            iconPath = "actions/add.svg",
        )
        writePnpLoader(
            workspace = workspace,
            designTokensLocation = "./.yarn/packages/design-tokens/",
            designTokensReference = "npm:0.322.0",
            iconsLocation = "./.yarn/packages/icons/",
            iconsReference = "npm:5.25.0",
        )

        assertFalse(Files.exists(workspace.resolve("node_modules")))
        assertEquals("#123456", resolvedValue(sourceFile))
        assertTrue("--tui-pnp-token" in tokenService.completionTokenNames(sourceFile))
        assertEquals(listOf("@tui.actions.add"), iconService.loadNow(sourceFile))
    }

    fun testPnpManifestUpdateInvalidatesTokenAndIconCaches() {
        val workspace = tempRoot.resolve("workspace-update")
        val sourceFile = createSourceFile(workspace)
        val firstTokensRoot = workspace.resolve(".yarn/packages/design-tokens-v1")
        val firstIconsRoot = workspace.resolve(".yarn/packages/icons-v1")
        val secondTokensRoot = workspace.resolve(".yarn/packages/design-tokens-v2")
        val secondIconsRoot = workspace.resolve(".yarn/packages/icons-v2")

        createDesignTokensPackage(firstTokensRoot, "1.0.0", "#111111")
        createIconsPackage(firstIconsRoot, "1.0.0", "old.svg")
        val pnpLoader =
            writePnpLoader(
                workspace = workspace,
                designTokensLocation = "./.yarn/packages/design-tokens-v1/",
                designTokensReference = "npm:1.0.0",
                iconsLocation = "./.yarn/packages/icons-v1/",
                iconsReference = "npm:1.0.0",
            )

        assertEquals("#111111", resolvedValue(sourceFile))
        assertEquals(listOf("@tui.old"), iconService.loadNow(sourceFile))

        createDesignTokensPackage(secondTokensRoot, "2.0.0", "#222222")
        createIconsPackage(secondIconsRoot, "2.0.0", "new.svg")
        writePnpLoader(
            workspace = workspace,
            designTokensLocation = "./.yarn/packages/design-tokens-v2/",
            designTokensReference = "npm:2.0.0",
            iconsLocation = "./.yarn/packages/icons-v2/",
            iconsReference = "npm:2.0.0",
        )

        assertEquals(1, tokenService.invalidate(listOf(pnpLoader)))
        assertEquals(1, iconService.invalidate(listOf(pnpLoader)))

        assertEquals("#222222", resolvedValue(sourceFile))
        assertEquals(listOf("@tui.new"), iconService.loadNow(sourceFile))
    }

    private fun resolvedValue(sourceFile: Path): String =
        tokenService
            .resolveToken(sourceFile, "--tui-pnp-token")
            .flatMap { group -> group.resolutions }
            .mapNotNull { resolution ->
                (resolution.result as? DesignTokenValueResolution.Resolved)?.value
            }.distinct()
            .single()

    private fun createSourceFile(workspace: Path): Path {
        val sourceFile = workspace.resolve("src/app.less")

        Files.createDirectories(sourceFile.parent)
        Files.writeString(sourceFile, ".app { color: var(--tui-pnp-token); }")

        return sourceFile
    }

    private fun createDesignTokensPackage(
        root: Path,
        version: String,
        tokenValue: String,
    ) {
        Files.createDirectories(root)
        Files.writeString(
            root.resolve("package.json"),
            """
            {
              "name": "@taiga-ui/design-tokens",
              "version": "$version"
            }
            """.trimIndent(),
        )
        Files.writeString(
            root.resolve("tokens.css"),
            """
            :root {
              --tui-pnp-token: $tokenValue;
            }
            """.trimIndent(),
        )
    }

    private fun createIconsPackage(
        root: Path,
        version: String,
        iconPath: String,
    ) {
        val icon = root.resolve("src").resolve(iconPath)

        Files.createDirectories(icon.parent)
        Files.writeString(
            root.resolve("package.json"),
            """
            {
              "name": "@taiga-ui/icons",
              "version": "$version"
            }
            """.trimIndent(),
        )
        Files.writeString(icon, "<svg></svg>")
    }

    private fun writePnpLoader(
        workspace: Path,
        designTokensLocation: String,
        designTokensReference: String,
        iconsLocation: String,
        iconsReference: String,
    ): Path {
        Files.createDirectories(workspace)
        val runtimeState =
            """
            {
              "enableTopLevelFallback": true,
              "packageRegistryData": [
                [
                  null,
                  [
                    [
                      null,
                      {
                        "packageLocation": "./",
                        "packageDependencies": [
                          ["@taiga-ui/design-tokens", "$designTokensReference"],
                          ["@taiga-ui/icons", "$iconsReference"]
                        ]
                      }
                    ]
                  ]
                ],
                [
                  "@taiga-ui/design-tokens",
                  [
                    [
                      "$designTokensReference",
                      {
                        "packageLocation": "$designTokensLocation",
                        "packageDependencies": []
                      }
                    ]
                  ]
                ],
                [
                  "@taiga-ui/icons",
                  [
                    [
                      "$iconsReference",
                      {
                        "packageLocation": "$iconsLocation",
                        "packageDependencies": []
                      }
                    ]
                  ]
                ]
              ]
            }
            """.trimIndent()
        val escapedState =
            runtimeState
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
        val loader = workspace.resolve(".pnp.cjs")

        Files.writeString(
            loader,
            """
            throw new Error("This file must never be executed by the plugin");
            const RAW_RUNTIME_STATE = '$escapedState';
            """.trimIndent(),
        )

        return loader
    }
}
