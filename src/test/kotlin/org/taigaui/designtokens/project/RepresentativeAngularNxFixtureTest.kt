package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RepresentativeAngularNxFixtureTest {
    @Test
    fun `builds a large reachable stylesheet scope without unrelated app styles`() =
        withWorkspace { workspaceRoot ->
            val fixture = RepresentativeAngularNxFixture.create(workspaceRoot)
            val graph = DesignTokenProjectStylesheetGraph()
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
