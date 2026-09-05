package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.DesignTokenSourceExtractor
import java.nio.file.Path

class ProjectStylesheetDeclarationCacheTest {
    @Test
    fun `reuses declarations for unchanged files and reextracts only changed file`() {
        val root = Path.of("build/fixtures/project-declaration-cache").toAbsolutePath().normalize()
        val firstFile = root.resolve("first.css")
        val secondFile = root.resolve("second.css")
        val stamps = mutableMapOf(firstFile to 1L, secondFile to 1L)
        val extractions = mutableMapOf<Path, Int>()
        val cache =
            ProjectStylesheetDeclarationCache(
                sourceExtractor =
                    DesignTokenSourceExtractor { sourceFile ->
                        extractions[sourceFile] = extractions.getOrDefault(sourceFile, 0) + 1
                        listOf(declaration(sourceFile, stamps.getValue(sourceFile)))
                    },
                modificationStampProvider =
                    ProjectStylesheetModificationStampProvider { sourceFile ->
                        stamps[sourceFile]
                    },
            )

        cache.extract(firstFile)
        cache.extract(secondFile)
        cache.extract(firstFile)
        cache.extract(secondFile)

        assertEquals(1, extractions[firstFile])
        assertEquals(1, extractions[secondFile])

        stamps[firstFile] = 2L

        assertEquals("2", cache.extract(firstFile).single().value)
        assertEquals("1", cache.extract(secondFile).single().value)
        assertEquals(2, extractions[firstFile])
        assertEquals(1, extractions[secondFile])
    }

    @Test
    fun `retries extraction when modification stamp changes during extraction`() {
        val sourceFile =
            Path.of("build/fixtures/project-declaration-cache-race/theme.css")
                .toAbsolutePath()
                .normalize()
        var stamp = 1L
        var extractions = 0
        val cache =
            ProjectStylesheetDeclarationCache(
                sourceExtractor =
                    DesignTokenSourceExtractor { path ->
                        extractions++
                        val extractedStamp = stamp

                        if (extractions == 1) {
                            stamp = 2L
                        }

                        listOf(declaration(path, extractedStamp))
                    },
                modificationStampProvider = ProjectStylesheetModificationStampProvider { stamp },
            )

        assertEquals("2", cache.extract(sourceFile).single().value)
        assertEquals(2, extractions)

        cache.extract(sourceFile)

        assertEquals(2, extractions)
    }

    private fun declaration(
        sourceFile: Path,
        stamp: Long,
    ): DesignTokenDeclaration =
        DesignTokenDeclaration(
            name = "--tui-test",
            value = stamp.toString(),
            sourceFile = sourceFile,
            line = 1,
        )
}
