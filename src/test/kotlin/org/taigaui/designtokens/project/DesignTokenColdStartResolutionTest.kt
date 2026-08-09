package org.taigaui.designtokens.project

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import java.nio.file.Files
import java.nio.file.Path

class DesignTokenColdStartResolutionTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var service: DesignTokenIndexService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("design-token-cold-start")
        service = project.getService(DesignTokenIndexService::class.java)
        service.clear()
    }

    override fun tearDown() {
        try {
            service.clear()
            tempRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testFirstResolutionWarmsPackageAndProjectCaches() {
        val workspaceRoot = tempRoot.resolve("workspace")
        val sourcePath = workspaceRoot.resolve("src/component.less")
        val packageRoot = workspaceRoot.resolve("node_modules/@taiga-ui/design-tokens")

        writeFile(sourcePath, "color: var($TOKEN_NAME);")
        writeFile(
            packageRoot.resolve("package.json"),
            """{"name":"@taiga-ui/design-tokens","version":"0.310.0"}""",
        )
        writeFile(
            packageRoot.resolve("typography.css"),
            ":root { $TOKEN_NAME: 800 2.75rem/1.1 sans-serif; }",
        )

        requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourcePath))

        assertFalse(service.isIndexCached(sourcePath))

        val resolutions =
            service
                .resolveToken(sourcePath, TOKEN_NAME)
                .flatMap { group -> group.resolutions }

        assertTrue(
            resolutions.any { resolution ->
                (resolution.result as? DesignTokenValueResolution.Resolved)?.value ==
                    "800 2.75rem/1.1 sans-serif"
            },
        )
        assertTrue(service.isIndexCached(sourcePath))
        assertEquals(1, service.cachedPackageCount)
        assertEquals(1, service.cachedProjectIndexCount)
    }

    private fun writeFile(
        path: Path,
        content: String,
    ) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private companion object {
        const val TOKEN_NAME = "--tui-font-heading-h2"
    }
}
