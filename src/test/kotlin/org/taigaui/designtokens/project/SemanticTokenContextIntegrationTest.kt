package org.taigaui.designtokens.project

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.diagnostics.PerformanceDiagnostics
import org.taigaui.designtokens.diagnostics.PerformanceMetric
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import java.nio.file.Files
import java.nio.file.Path

class SemanticTokenContextIntegrationTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var service: DesignTokenIndexService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("semantic-token-context")
        service = project.getService(DesignTokenIndexService::class.java)
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

    fun testSharesProjectIndexAndSnapshotAcrossFilesWithSameEffectiveContext() {
        val fixture = RepresentativeAngularNxFixture.create(tempRoot.resolve("workspace"))
        val firstSource = createFile(fixture.projectRoot.resolve("src/app/first.ts"), "export const first = true;")
        val secondSource = createFile(fixture.projectRoot.resolve("src/app/second.html"), "<div></div>")

        assertEquals("39", resolvedValue(firstSource, TOKEN_NAME))
        assertEquals("39", resolvedValue(secondSource, TOKEN_NAME))
        assertEquals(service.contextKey(firstSource), service.contextKey(secondSource))
        assertEquals(1, service.cachedProjectIndexCount)
        assertEquals(1, service.cachedResolutionSnapshotCount)

        PerformanceDiagnostics.snapshot().let { snapshot ->
            assertEquals(1L, snapshot.getValue(PerformanceMetric.PROJECT_GRAPH_BUILD).count)
            assertEquals(1L, snapshot.getValue(PerformanceMetric.INDEX_COMPOSITION).count)
            assertEquals(41L, snapshot.getValue(PerformanceMetric.PSI_EXTRACTION).count)
        }
    }

    fun testKeepsCurrentStylesheetEntrypointsInDistinctContexts() {
        val fixture = RepresentativeAngularNxFixture.create(tempRoot.resolve("workspace"))
        val firstSource =
            createFile(
                fixture.projectRoot.resolve("src/app/first.css"),
                ":root { --tui-local-context: red; }",
            )
        val secondSource =
            createFile(
                fixture.projectRoot.resolve("src/app/second.css"),
                ":root { --tui-local-context: blue; }",
            )

        assertEquals("red", resolvedValue(firstSource, LOCAL_TOKEN_NAME))
        assertEquals("blue", resolvedValue(secondSource, LOCAL_TOKEN_NAME))
        assertNotSame(service.contextKey(firstSource), service.contextKey(secondSource))
        assertEquals(2, service.cachedProjectIndexCount)
        assertEquals(2, service.cachedResolutionSnapshotCount)
    }

    private fun resolvedValue(
        sourceFile: Path,
        tokenName: String,
    ): String =
        service
            .resolveToken(sourceFile, tokenName)
            .flatMap { group -> group.resolutions }
            .mapNotNull { resolution -> resolution.result as? DesignTokenValueResolution.Resolved }
            .single()
            .value

    private fun createFile(
        path: Path,
        content: String,
    ): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)

        return path
    }

    private companion object {
        const val TOKEN_NAME = "--tui-token-39"
        const val LOCAL_TOKEN_NAME = "--tui-local-context"
    }
}
