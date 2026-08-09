package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path

class ProjectStylesheetIndexCacheTest {
    @Test
    fun `invalidates project entries for stylesheet changes but ignores node modules`() {
        val workspaceRoot = Path.of("build/fixtures/project-cache").toAbsolutePath().normalize()
        val request =
            ProjectStylesheetIndexRequest(
                sourceFile = workspaceRoot.resolve("src/component.scss"),
                workspaceRoot = workspaceRoot,
            )
        val cache =
            ProjectStylesheetIndexCache { cacheRequest ->
                DesignTokenIndex.build(cacheRequest.workspaceRoot, emptyList())
            }

        assertFalse(cache.contains(request))

        cache.getOrBuild(request)

        assertTrue(cache.contains(request))
        assertEquals(
            0,
            cache.invalidate(
                listOf(workspaceRoot.resolve("node_modules/@taiga-ui/core/styles/variables.less")),
            ),
        )
        assertTrue(cache.contains(request))
        assertEquals(1, cache.invalidate(listOf(workspaceRoot.resolve("src/theme.scss"))))
        assertFalse(cache.contains(request))
    }
}
