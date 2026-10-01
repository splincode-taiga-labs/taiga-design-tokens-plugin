package org.taigaui.designtokens.completion

import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenDeprecation
import org.taigaui.designtokens.project.DesignTokenCatalogEntry
import org.taigaui.designtokens.project.DesignTokenCatalogSource

class DesignTokenCompletionPresentationTest {
    @Test
    fun `shows source scope in native completion type text`() {
        assertTypeText(
            DesignTokenCatalogEntry(
                name = "--tui-project",
                source = DesignTokenCatalogSource.PROJECT,
            ),
            "project",
        )
        assertTypeText(
            DesignTokenCatalogEntry(
                name = "--tui-override",
                source = DesignTokenCatalogSource.PROJECT_OVERRIDE,
            ),
            "project override",
        )
        assertTypeText(
            DesignTokenCatalogEntry(
                name = "--tui-installed",
                source = DesignTokenCatalogSource.INSTALLED_PACKAGE,
                packageName = "@taiga-ui/design-tokens",
            ),
            "@taiga-ui/design-tokens",
        )
    }

    @Test
    fun `keeps deprecated marker alongside source scope`() {
        val entry =
            DesignTokenCatalogEntry(
                name = "--tui-old",
                deprecation = DesignTokenDeprecation(message = "Use --tui-new"),
                source = DesignTokenCatalogSource.INSTALLED_PACKAGE,
                packageName = "@taiga-ui/design-tokens",
            )
        val presentation = LookupElementPresentation()

        entry.toLookupElement().renderElement(presentation)

        assertEquals("@taiga-ui/design-tokens", presentation.typeText)
        assertTrue(presentation.isStrikeout)
        assertTrue(presentation.tailText.orEmpty().contains("deprecated"))
    }

    private fun assertTypeText(
        entry: DesignTokenCatalogEntry,
        expected: String,
    ) {
        val presentation = LookupElementPresentation()

        entry.toLookupElement().renderElement(presentation)

        assertEquals(expected, presentation.typeText)
    }
}
