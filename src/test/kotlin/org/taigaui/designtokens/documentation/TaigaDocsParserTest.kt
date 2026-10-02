package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaigaDocsParserTest {
    private val parser = TaigaDocsParser()

    @Test
    fun parsesCurrentV5ImportMapComponentDocsInputsOutputsAndSelectors() {
        val index = requireNotNull(parser.parse(requireNotNull(TaigaDocsSources.forMajor(5)), v5Docs()))
        val button = index.findByPublicSymbol("TuiButton").single()
        val calendar = index.findBySelector("tui-calendar").single()

        assertEquals("components/button", button.sectionId)
        assertEquals(setOf("@taiga-ui/core"), button.packageNames)
        assertEquals(TaigaDocKind.COMPONENT, button.kind)
        assertEquals(setOf("TuiButton", "TuiButtonOptions"), button.publicSymbols)
        assertEquals(setOf("tuiButton"), button.selectors)
        assertEquals("size", button.inputs.single().name)
        assertEquals("TuiSizeXS | TuiSizeL", button.inputs.single().documentedType)
        assertTrue(requireNotNull(button.description).startsWith("Button is a basic component"))
        assertEquals("html", requireNotNull(button.example).language)
        assertEquals("dayClick", calendar.outputs.single().name)
        assertEquals("TuiDay", calendar.outputs.single().documentedType)
        assertEquals("https://taiga-ui.dev/components/calendar", calendar.documentationUri.toString())
        assertNotNull(index.findBySectionId("components/CALENDAR"))
    }

    @Test
    fun parsesRepresentativeV4DocsWithVersionedLinks() {
        val index = requireNotNull(parser.parse(requireNotNull(TaigaDocsSources.forMajor(4)), v4Docs()))
        val input = index.findByPublicSymbol("TuiInputModule").single()

        assertEquals("4.60.0", input.version)
        assertEquals(setOf("@taiga-ui/legacy"), input.packageNames)
        assertEquals(setOf("tui-input"), input.selectors)
        assertEquals("https://taiga-ui.dev/v4/components/input", input.documentationUri.toString())
    }

    @Test
    fun rejectsEmptyAndMalformedDocuments() {
        val source = requireNotNull(TaigaDocsSources.forMajor(5))

        assertEquals(null, parser.parse(source, ""))
        assertEquals(null, parser.parse(source, "not Taiga UI documentation"))
    }

    private fun v5Docs(): String =
        listOf(
            "# Import Map - Package Exports Reference",
            "",
            "## @taiga-ui/core",
            "",
            "**Components:**",
            "",
            "### button",
            FENCE + "text",
            "TuiButton, TuiButtonOptions",
            FENCE,
            "",
            "### calendar",
            FENCE + "text",
            "TuiCalendar",
            FENCE,
            "",
            "# components/Button",
            "- **Package**: " + tick("CORE"),
            "- **Type**: components",
            "- **Version**: 5.0.0",
            "",
            "Button is a basic component used to trigger actions.",
            "",
            "### Example",
            FENCE + "html",
            "<button tuiButton size=\"m\">Button</button>",
            FENCE,
            "",
            "### API - Inputs",
            "| Property | Type | Description |",
            "| --- | --- | --- |",
            "| " + tick("[size]") + " | " + tick("TuiSizeXS \\| TuiSizeL") + " | Button size |",
            "",
            "# components/Calendar",
            "- **Package**: " + tick("CORE"),
            "- **Type**: components",
            "- **Version**: 5.0.0",
            "",
            "Calendar displays a month.",
            "",
            "### Example",
            FENCE + "html",
            "<tui-calendar (dayClick)=\"onDayClick(\$event)\" />",
            FENCE,
            "",
            "### API - Outputs",
            "| Event | Type | Description |",
            "| --- | --- | --- |",
            "| " + tick("(dayClick)") + " | " + tick("TuiDay") + " | Date click |",
        ).joinToString("\n")

    private fun v4Docs(): String =
        listOf(
            "# Import Map - Package Exports Reference",
            "",
            "## @taiga-ui/legacy",
            "",
            "**Components:**",
            "",
            "### input",
            FENCE + "text",
            "TuiInputModule",
            FENCE,
            "",
            "# components/Input",
            "- **Package**: " + tick("LEGACY"),
            "- **Type**: components",
            "- **Version**: 4.60.0",
            "",
            "Legacy input component.",
            "",
            "### Example",
            FENCE + "html",
            "<tui-input>Value</tui-input>",
            FENCE,
        ).joinToString("\n")

    private fun tick(value: String): String = BACKTICK + value + BACKTICK

    private companion object {
        const val BACKTICK = "\u0060"
        const val FENCE = "\u0060\u0060\u0060"
    }
}
