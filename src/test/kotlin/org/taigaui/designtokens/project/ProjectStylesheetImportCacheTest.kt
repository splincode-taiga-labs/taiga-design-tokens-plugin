package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class ProjectStylesheetImportCacheTest {
    @Test
    fun `reuses parsed imports for unchanged files and reparses only changed file`() {
        val root = Path.of("build/fixtures/project-import-cache").toAbsolutePath().normalize()
        val firstFile = root.resolve("first.scss")
        val secondFile = root.resolve("second.scss")
        val stamps = mutableMapOf(firstFile to 1L, secondFile to 1L)
        val contents =
            mutableMapOf(
                firstFile to "@use './first-theme';",
                secondFile to "@use './second-theme';",
            )
        val reads = mutableMapOf<Path, Int>()
        val cache =
            ProjectStylesheetImportCache(
                readText = { sourceFile ->
                    reads[sourceFile] = reads.getOrDefault(sourceFile, 0) + 1
                    contents[sourceFile]
                },
                modificationStampProvider =
                    ProjectStylesheetModificationStampProvider { sourceFile ->
                        stamps[sourceFile]
                    },
            )

        cache.imports(firstFile)
        cache.imports(secondFile)
        cache.imports(firstFile)
        cache.imports(secondFile)

        assertEquals(1, reads[firstFile])
        assertEquals(1, reads[secondFile])

        contents[firstFile] = "@forward './changed-theme';"
        stamps[firstFile] = 2L

        assertEquals(listOf("./changed-theme"), cache.imports(firstFile))
        assertEquals(listOf("./second-theme"), cache.imports(secondFile))
        assertEquals(2, reads[firstFile])
        assertEquals(1, reads[secondFile])
    }

    @Test
    fun `retries parsing when modification stamp changes while reading`() {
        val sourceFile =
            Path
                .of("build/fixtures/project-import-cache-race/theme.scss")
                .toAbsolutePath()
                .normalize()
        var stamp = 1L
        var reads = 0
        val cache =
            ProjectStylesheetImportCache(
                readText = {
                    reads++

                    if (reads == 1) {
                        stamp = 2L
                        "@use './stale';"
                    } else {
                        "@use './fresh';"
                    }
                },
                modificationStampProvider = ProjectStylesheetModificationStampProvider { stamp },
            )

        assertEquals(listOf("./fresh"), cache.imports(sourceFile))
        assertEquals(2, reads)

        cache.imports(sourceFile)

        assertEquals(2, reads)
    }

    @Test
    fun `invalidation removes renamed or deleted paths`() {
        val root = Path.of("build/fixtures/project-import-cache-invalidation").toAbsolutePath().normalize()
        val sourceFile = root.resolve("theme.scss")
        var reads = 0
        val cache =
            ProjectStylesheetImportCache(
                readText = {
                    reads++
                    "@use './tokens';"
                },
                modificationStampProvider = ProjectStylesheetModificationStampProvider { 1L },
            )

        cache.imports(sourceFile)
        cache.invalidate(listOf(root))
        cache.imports(sourceFile)

        assertEquals(2, reads)
    }
}
