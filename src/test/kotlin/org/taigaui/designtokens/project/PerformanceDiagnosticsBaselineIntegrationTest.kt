package org.taigaui.designtokens.project

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.diagnostics.PerformanceDiagnostics
import org.taigaui.designtokens.diagnostics.PerformanceMetric
import java.nio.file.Files
import java.nio.file.Path

class PerformanceDiagnosticsBaselineIntegrationTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var service: DesignTokenIndexService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("performance-diagnostics-baseline")
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

    fun testRepresentativeFixtureCapturesColdAndHotResolutionBaseline() {
        val fixture = RepresentativeAngularNxFixture.create(tempRoot.resolve("workspace"))

        assertTrue(service.resolveToken(fixture.sourceFile, TOKEN_NAME).isNotEmpty())
        assertTrue(service.resolveToken(fixture.sourceFile, TOKEN_NAME).isNotEmpty())

        val snapshot = PerformanceDiagnostics.snapshot()

        assertEquals(1L, snapshot.getValue(PerformanceMetric.PROJECT_GRAPH_BUILD).count)
        assertEquals(42L, snapshot.getValue(PerformanceMetric.PSI_EXTRACTION).count)
        assertEquals(2L, snapshot.getValue(PerformanceMetric.INDEX_COMPOSITION).count)
        assertEquals(2L, snapshot.getValue(PerformanceMetric.VALUE_RESOLUTION).count)
    }

    fun testUnrelatedStylesheetChangeDoesNotRebuildRepresentativeProjectIndex() {
        val fixture = RepresentativeAngularNxFixture.create(tempRoot.resolve("workspace"))

        assertTrue(service.resolveToken(fixture.sourceFile, TOKEN_NAME).isNotEmpty())
        assertEquals(0, service.invalidate(listOf(fixture.unrelatedStylesheet)))
        assertTrue(service.resolveToken(fixture.sourceFile, TOKEN_NAME).isNotEmpty())

        PerformanceDiagnostics.snapshot().let { snapshot ->
            assertEquals(1L, snapshot.getValue(PerformanceMetric.PROJECT_GRAPH_BUILD).count)
            assertEquals(42L, snapshot.getValue(PerformanceMetric.PSI_EXTRACTION).count)
        }

        assertEquals(1, service.invalidate(listOf(fixture.reachableStylesheets.last())))
        assertTrue(service.resolveToken(fixture.sourceFile, TOKEN_NAME).isNotEmpty())

        PerformanceDiagnostics.snapshot().let { snapshot ->
            assertEquals(2L, snapshot.getValue(PerformanceMetric.PROJECT_GRAPH_BUILD).count)
            assertEquals(43L, snapshot.getValue(PerformanceMetric.PSI_EXTRACTION).count)
        }
    }

    private companion object {
        const val TOKEN_NAME = "--tui-token-39"
    }
}
