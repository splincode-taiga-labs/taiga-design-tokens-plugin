package org.taigaui.designtokens.icons

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class IconCatalogFreshnessPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `local icon package does not expire`() {
        val workspace = workspace()

        createIcon(workspace, "icons/src/a-arrow-down.svg")

        assertEquals(
            IconCatalogCachePolicy.LOCAL,
            loadResult(workspace, IconCatalogFetcher { null }).cachePolicy,
        )
    }

    @Test
    fun `successful proprietary CDN catalog gets regular remote lifetime`() {
        val workspace = workspace()

        createPackage(workspace, "proprietary")

        val result =
            loadResult(
                workspace,
                IconCatalogFetcher {
                    """
                    {
                      "icons": {
                        "fancy/medium": ["info-circle"]
                      }
                    }
                    """.trimIndent()
                },
            )

        assertEquals(IconCatalogCachePolicy.REMOTE_SUCCESS, result.cachePolicy)
        assertEquals(listOf("@tui.fancy.medium.info-circle"), result.catalog.names)
    }

    @Test
    fun `failed or empty proprietary CDN catalog gets short retry lifetime`() {
        val workspace = workspace()

        createPackage(workspace, "proprietary")

        assertEquals(
            IconCatalogCachePolicy.REMOTE_RETRY,
            loadResult(workspace, IconCatalogFetcher { null }).cachePolicy,
        )
        assertEquals(
            IconCatalogCachePolicy.REMOTE_RETRY,
            loadResult(workspace, IconCatalogFetcher { "{}" }).cachePolicy,
        )
    }

    private fun loadResult(
        workspace: Path,
        fetcher: IconCatalogFetcher,
    ): IconCatalogLoadResult {
        val sourceFile = workspace.resolve("src/app.ts")

        Files.createDirectories(sourceFile.parent)
        Files.writeString(sourceFile, "const icon = '@tui.';")

        val loader = IconCatalogLoader(fetcher)
        val scopeRoot = requireNotNull(loader.resolveScopeRoot(sourceFile))

        return loader.loadCatalogWithPolicy(scopeRoot)
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
