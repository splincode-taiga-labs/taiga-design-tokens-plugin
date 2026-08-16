package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokenNotFoundModelTest {
    @Test
    fun `shows suggestions below nothing found message`() {
        val model =
            DesignTokenHoverPopupModel.notFound(
                tokenName = "--tui-border",
                suggestions =
                    listOf(
                        "--tui-border-hover",
                        "--tui-border-normal",
                    ),
            )

        assertEquals(
            """
            Nothing found

            Did you mean another variable?
            --tui-border-hover
            --tui-border-normal
            """.trimIndent(),
            model.description,
        )
        assertTrue(model.sections.isEmpty())
    }

    @Test
    fun `keeps simple nothing found message without suggestions`() {
        val model = DesignTokenHoverPopupModel.notFound("--tui-unknown", emptyList())

        assertEquals("Nothing found", model.description)
    }
}
