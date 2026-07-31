package org.taigaui.designtokens.tokenindex

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class DesignTokenSourceFileFinderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val finder = DesignTokenSourceFileFinder()

    @Test
    fun `finds supported source files recursively in stable order`() {
        val packageRoot = temporaryFolder.newFolder("package").toPath()
        val css = createFile(packageRoot.resolve("styles/a.css"))
        val scss = createFile(packageRoot.resolve("themes/dark.scss"))
        val less = createFile(packageRoot.resolve("themes/light.less"))
        createFile(packageRoot.resolve("index.js"))
        createFile(packageRoot.resolve("README.md"))

        val result = finder.find(packageRoot)

        val expected = listOf(css, scss, less)
            .map { it.toAbsolutePath().normalize() }
            .sortedBy(Path::toString)

        assertEquals(expected, result)
    }

    @Test
    fun `returns empty list for missing directory`() {
        val missingRoot = temporaryFolder.root.toPath().resolve("missing")

        assertEquals(emptyList<Path>(), finder.find(missingRoot))
    }

    private fun createFile(path: Path): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, "")

        return path
    }
}
