package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
