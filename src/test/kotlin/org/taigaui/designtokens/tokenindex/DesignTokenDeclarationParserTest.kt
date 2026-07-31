package org.taigaui.designtokens.tokenindex

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class DesignTokenDeclarationParserTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val parser = DesignTokenDeclarationParser()

    @Test
    fun `parses declarations with values and source locations`() {
        val sourceFile = createSourceFile(
            "tokens.scss",
            """
            :root {
                --tui-text-primary: #000;
                --tui-shadow:
                    0 1px 2px rgb(0 0 0 / 10%);
                --tui-data: url("data:image/svg+xml;utf8,<svg></svg>");
                --tui-last: white
            }
            """.trimIndent(),
        )
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()

        val result = parser.parse(sourceFile)

        assertEquals(
            listOf(
                DesignTokenDeclaration(
                    name = "--tui-text-primary",
                    value = "#000",
                    sourceFile = normalizedSourceFile,
                    line = 2,
                ),
                DesignTokenDeclaration(
                    name = "--tui-shadow",
                    value = "0 1px 2px rgb(0 0 0 / 10%)",
                    sourceFile = normalizedSourceFile,
                    line = 3,
                ),
                DesignTokenDeclaration(
                    name = "--tui-data",
                    value = "url(\"data:image/svg+xml;utf8,<svg></svg>\")",
                    sourceFile = normalizedSourceFile,
                    line = 5,
                ),
                DesignTokenDeclaration(
                    name = "--tui-last",
                    value = "white",
                    sourceFile = normalizedSourceFile,
                    line = 6,
                ),
            ),
            result,
        )
    }

    @Test
    fun `ignores references comments strings and unrelated properties`() {
        val sourceFile = createSourceFile(
            "ignored.less",
            """
            :root {
                color: var(--tui-text-primary);
                --company-token: red;
                /* --tui-block-commented: red; */
                // --tui-line-commented: blue;
                content: "--tui-string-token: green;";
            }
            """.trimIndent(),
        )

        assertEquals(emptyList<DesignTokenDeclaration>(), parser.parse(sourceFile))
    }

    @Test
    fun `supports whitespace and comments before the colon`() {
        val sourceFile = createSourceFile(
            "spacing.css",
            "--tui-text-warning /* generated */ : rgb(255 100 0);",
        )

        val result = parser.parse(sourceFile)

        assertEquals(1, result.size)
        assertEquals("--tui-text-warning", result.single().name)
        assertEquals("rgb(255 100 0)", result.single().value)
        assertEquals(1, result.single().line)
    }

    @Test
    fun `returns empty list when source file cannot be read`() {
        val missingFile = temporaryFolder.root.toPath().resolve("missing.css")

        assertEquals(emptyList<DesignTokenDeclaration>(), parser.parse(missingFile))
    }

    private fun createSourceFile(name: String, content: String): Path {
        val sourceFile = temporaryFolder.root.toPath().resolve(name)
        Files.writeString(sourceFile, content)

        return sourceFile
    }
}
