package org.taigaui.designtokens.project

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class RepresentativeAngularNxFixtureTest : BasePlatformTestCase() {
    fun testBuildsALargeReachableStylesheetScopeWithoutUnrelatedAppStyles() =
        withWorkspace { workspaceRoot ->
            val fixture = RepresentativeAngularNxFixture.create(workspaceRoot)
            val graph = DesignTokenProjectStylesheetGraph(project)
            val scope =
                graph.buildScope(
                    graph.createRequest(
                        sourceFile = fixture.sourceFile,
                        workspaceRootHint = workspaceRoot,
                    ),
                )

            assertEquals(fixture.projectRoot.normalized(), scope.projectRoot)
            assertEquals(
                fixture.reachableStylesheets.map { path -> path.normalized() }.toSet(),
                scope.sourceFiles.toSet(),
            )
            assertFalse(scope.sourceFiles.contains(fixture.unrelatedStylesheet.normalized()))
        }

    private fun withWorkspace(block: (Path) -> Unit) {
        val workspaceRoot = Files.createTempDirectory("representative-angular-nx")

        try {
            block(workspaceRoot)
        } finally {
            workspaceRoot.toFile().deleteRecursively()
        }
    }
}

private fun Path.normalized(): Path = toAbsolutePath().normalize()
