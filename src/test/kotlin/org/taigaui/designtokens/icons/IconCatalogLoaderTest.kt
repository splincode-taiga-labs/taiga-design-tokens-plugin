package org.taigaui.designtokens.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class IconCatalogLoaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `maps public svg paths to tui icon names`() {
        val workspace = workspace()

        createIcon(workspace, "icons/src/a-arrow-down.svg")
        createIcon(workspace, "icons/src/flags/ab.svg")

        val names = load(workspace)

        assertTrue("@tui.a-arrow-down" in names)
        assertTrue("@tui.flags.ab" in names)
    }

    @Test
    fun `prefers installed tds icons over remote proprietary catalog`() {
        val workspace = workspace()
        var remoteRequested = false

        createPackage(workspace, "proprietary")
        createIcon(workspace, "tds-icons/src/fancy/medium/info-circle.svg")
        createIcon(workspace, "tds-icons/src/fancy/medium/alert.svg")
        createIcon(workspace, "tds-icons/src/fancy/medium/check-circle.svg")

        val names =
            load(
                workspace = workspace,
                fetcher =
                    IconCatalogFetcher {
                        remoteRequested = true
                        null
                    },
            )

        assertFalse(remoteRequested)
        assertTrue("@tui.fancy.medium.info-circle" in names)
        assertTrue("@tui.fancy.medium.alert" in names)
        assertTrue("@tui.fancy.medium.check-circle" in names)
    }

    @Test
    fun `falls back to tbank icon catalog when proprietary package has no tds icons`() {
        val workspace = workspace()

        createPackage(workspace, "proprietary")

        val names =
            load(
                workspace = workspace,
                fetcher =
                    IconCatalogFetcher {
                        """
                        {
                          "version": "v1",
                          "icons": {
                            "emoji": ["bank"],
                            "fancy/medium": ["air-hockey", "info-circle", "alert", "check-circle"]
                          }
                        }
                        """.trimIndent()
                    },
            )

        assertEquals(
            setOf(
                "@tui.emoji.bank",
                "@tui.fancy.medium.air-hockey",
                "@tui.fancy.medium.info-circle",
                "@tui.fancy.medium.alert",
                "@tui.fancy.medium.check-circle",
            ),
            names.toSet(),
        )
    }

    private fun load(
        workspace: Path,
        fetcher: IconCatalogFetcher = IconCatalogFetcher { null },
    ): List<String> {
        val sourceFile = workspace.resolve("src/app.ts")

        Files.createDirectories(sourceFile.parent)
        Files.writeString(sourceFile, "const icon = '@tui.';")

        val loader = IconCatalogLoader(fetcher)
        val scopeRoot = requireNotNull(loader.resolveScopeRoot(sourceFile))

        return loader.load(scopeRoot)
    }

    private fun workspace(): Path = temporaryFolder.newFolder().toPath()

    private fun createPackage(
        workspace: Path,
        name: String,
    ) {
        val packageRoot = workspace.resolve("node_modules/@taiga-ui/$name")

        Files.createDirectories(packageRoot)
        Files.writeString(packageRoot.resolve("package.json"), "{\"name\":\"@taiga-ui/$name\"}")
    }

    private fun createIcon(
        workspace: Path,
        relativePath: String,
    ) {
        val file = workspace.resolve("node_modules/@taiga-ui/$relativePath")

        Files.createDirectories(file.parent)
        Files.writeString(file, "<svg></svg>")
    }
}
