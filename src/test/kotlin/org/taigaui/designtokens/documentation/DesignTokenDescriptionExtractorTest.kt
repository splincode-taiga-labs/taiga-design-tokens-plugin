package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.index.DesignTokenSourceFormat
import java.nio.file.Files
import java.nio.file.attribute.FileTime

class DesignTokenDescriptionExtractorTest {
    @Test
    fun `extracts a trailing block comment`() {
        val description =
            DesignTokenDescriptionExtractor.extract(
                lines =
                    listOf(
                        ":root {",
                        "    --tui-background-overlay-glass-on-light: var(--tui-const-black-alpha-40); /* Оверлей стеклянных поверхностей на светлых фонах, не меняется при смене темы */",
                        "}",
                    ),
                declarationLine = 2,
            )

        assertEquals(
            "Оверлей стеклянных поверхностей на светлых фонах, не меняется при смене темы",
            description,
        )
    }

    @Test
    fun `extracts an adjacent multiline block comment`() {
        val description =
            DesignTokenDescriptionExtractor.extract(
                lines =
                    listOf(
                        "/**",
                        " * Text color for secondary content.",
                        " * Use it for supporting labels.",
                        " */",
                        "--tui-text-secondary: var(--tui-const-black-alpha-54);",
                    ),
                declarationLine = 5,
            )

        assertEquals(
            "Text color for secondary content. Use it for supporting labels.",
            description,
        )
    }

    @Test
    fun `extracts adjacent line comments for less and scss`() {
        val description =
            DesignTokenDescriptionExtractor.extract(
                lines =
                    listOf(
                        "// Text color for secondary content.",
                        "// It is shared by mobile themes.",
                        "--tui-text-secondary: var(--tui-const-cool-gray-lighter-60);",
                    ),
                declarationLine = 3,
            )

        assertEquals(
            "Text color for secondary content. It is shared by mobile themes.",
            description,
        )
    }

    @Test
    fun `does not associate a comment separated by an empty line`() {
        val description =
            DesignTokenDescriptionExtractor.extract(
                lines =
                    listOf(
                        "/* Section colors */",
                        "",
                        "--tui-text-secondary: var(--tui-const-black-alpha-54);",
                    ),
                declarationLine = 3,
            )

        assertNull(description)
    }

    @Test
    fun `ignores tool directives`() {
        val description =
            DesignTokenDescriptionExtractor.extract(
                lines =
                    listOf(
                        "/* stylelint-disable custom-property-pattern */",
                        "--tui-text-secondary: var(--tui-const-black-alpha-54);",
                    ),
                declarationLine = 2,
            )

        assertNull(description)
    }

    @Test
    fun `refreshes a cached source after the file changes`() {
        val sourceFile = Files.createTempFile("design-token-description", ".css")
        val origin =
            DesignTokenOrigin(
                sourceFile = sourceFile,
                line = 1,
                format = DesignTokenSourceFormat.CSS,
                selectorChain = listOf(":root"),
            )

        try {
            Files.writeString(sourceFile, "--tui-token: #fff; /* First description */")
            assertEquals("First description", DesignTokenDescriptionExtractor.extract(listOf(origin)))

            Files.writeString(sourceFile, "--tui-token: #fff; /* Updated description */")
            Files.setLastModifiedTime(sourceFile, FileTime.fromMillis(System.currentTimeMillis() + 2_000))

            assertEquals("Updated description", DesignTokenDescriptionExtractor.extract(listOf(origin)))
        } finally {
            Files.deleteIfExists(sourceFile)
        }
    }
}
