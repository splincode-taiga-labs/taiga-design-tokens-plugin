package org.taigaui.designtokens.icons

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.diagnostics.PerformanceDiagnostics
import org.taigaui.designtokens.diagnostics.PerformanceMetric
import java.nio.file.Files
import java.nio.file.Path

class IconCatalogInvalidationIntegrationTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var service: IconCompletionService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("icon-catalog-invalidation")
        service = project.getService(IconCompletionService::class.java)
        service.clear()
        PerformanceDiagnostics.reset()
        PerformanceDiagnostics.setEnabledForTests(true)
    }

    override fun tearDown() {
        try {
            service.clear()
            PerformanceDiagnostics.reset()
            PerformanceDiagnostics.setEnabledForTests(null)
            tempRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testRebuildsLocalCatalogLazilyAfterSvgInvalidation() {
        val workspace = tempRoot.resolve("workspace")
        val firstIcon = createIcon(workspace, "icons/src/first.svg")
        val sourceFile = createSourceFile(workspace)

        assertEquals(listOf("@tui.first"), service.loadNow(sourceFile))
        assertEquals(1L, iconLoads())

        val secondIcon = createIcon(workspace, "icons/src/second.svg")

        assertEquals(listOf("@tui.first"), service.loadNow(sourceFile))
        assertEquals(1L, iconLoads())

        assertEquals(1, service.invalidate(listOf(secondIcon)))
        assertEquals(1L, iconLoads())

        assertEquals(listOf("@tui.first", "@tui.second"), service.loadNow(sourceFile))
        assertEquals(2L, iconLoads())
        assertTrue(Files.isRegularFile(firstIcon))
    }

    fun testPackageInvalidationRefreshesEffectiveLocalIconSource() {
        val workspace = tempRoot.resolve("workspace")
        val sourceFile = createSourceFile(workspace)

        createIcon(workspace, "icons/src/public.svg")

        assertEquals(listOf("@tui.public"), service.loadNow(sourceFile))

        val proprietaryPackage = createPackage(workspace, "proprietary")

        createIcon(workspace, "tds-icons/src/fancy/medium/private.svg")

        assertEquals(1, service.invalidate(listOf(proprietaryPackage.resolve("package.json"))))
        assertEquals(
            listOf("@tui.fancy.medium.private"),
            service.loadNow(sourceFile),
        )
    }

    private fun iconLoads(): Long =
        PerformanceDiagnostics
            .snapshot()
            .getValue(PerformanceMetric.ICON_CATALOG_LOAD)
            .count

    private fun createSourceFile(workspace: Path): Path {
        val sourceFile = workspace.resolve("src/app.ts")

        Files.createDirectories(sourceFile.parent)
        Files.writeString(sourceFile, "const icon = '@tui.';")

        return sourceFile
    }

    private fun createPackage(
        workspace: Path,
        name: String,
    ): Path {
        val packageRoot = workspace.resolve("node_modules/@taiga-ui/$name")

        Files.createDirectories(packageRoot)
        Files.writeString(packageRoot.resolve("package.json"), "{\"name\":\"@taiga-ui/$name\"}")

        return packageRoot
    }

    private fun createIcon(
        workspace: Path,
        relativePath: String,
    ): Path {
        val file = workspace.resolve("node_modules/@taiga-ui/$relativePath")

        Files.createDirectories(file.parent)
        Files.writeString(file, "<svg></svg>")

        return file
    }
}
