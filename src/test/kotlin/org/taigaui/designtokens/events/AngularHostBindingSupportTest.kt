package org.taigaui.designtokens.events

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AngularHostBindingSupportTest : LightPlatformCodeInsightFixture4TestCase() {
    @Test
    fun `finds event plugin bindings in directive host metadata`() {
        val file =
            myFixture.configureByText(
                "directive.ts",
                """
                @Directive({
                    selector: '[example]',
                    host: {
                        '(mousemove.zoneless)': 'onMove(${DOLLAR}event)',
                        '(keydown.enter.stop)': 'onKey(${DOLLAR}event)',
                    },
                })
                export class ExampleDirective {}
                """.trimIndent(),
            )

        val bindings = AngularHostBindingSupport.findAll(file)

        assertEquals(
            listOf("(mousemove.zoneless)", "(keydown.enter.stop)"),
            bindings.map(AngularHostEventBinding::source),
        )
        bindings.forEach { binding ->
            assertEquals(binding.source, file.text.substring(binding.startOffset, binding.endOffset))
        }
    }

    @Test
    fun `finds host binding at modifier offset`() {
        val file =
            myFixture.configureByText(
                "component.ts",
                """
                @Component({
                    selector: 'example',
                    host: {'(click.zoneless.capture)': 'onClick()'},
                })
                export class ExampleComponent {}
                """.trimIndent(),
            )
        val offset = file.text.indexOf("zoneless") + 2
        val binding = AngularHostBindingSupport.findAt(file, offset)

        assertNotNull(binding)
        assertEquals("(click.zoneless.capture)", binding?.source)
    }

    @Test
    fun `ignores similar keys outside Angular host metadata`() {
        val file =
            myFixture.configureByText(
                "plain.ts",
                """
                const ordinary = {'(click.zoneless)': 'value'};

                @Directive({
                    selector: '[example]',
                    options: {
                        host: {'(click.stop)': 'notAngularHost'},
                    },
                })
                export class ExampleDirective {}
                """.trimIndent(),
            )

        assertTrue(AngularHostBindingSupport.findAll(file).isEmpty())
    }

    @Test
    fun `uses host binding offsets for modifier validation`() {
        val file =
            myFixture.configureByText(
                "invalid.ts",
                """
                @Directive({
                    selector: '[example]',
                    host: {
                        '(click.captre)': 'onClick()',
                        '(click.zoneless.zoneless)': 'onClick()',
                    },
                })
                export class ExampleDirective {}
                """.trimIndent(),
            )
        val bindings = AngularHostBindingSupport.findAll(file)
        val unknown =
            EventPluginUnknownModifierFinder.findInEventName(
                bindings[0].eventName,
                bindings[0].eventNameStartOffset,
            ).single()
        val duplicate =
            EventPluginDuplicateModifierFinder.findInEventName(
                bindings[1].eventName,
                bindings[1].eventNameStartOffset,
            ).single()

        assertEquals("captre", file.text.substring(unknown.startOffset, unknown.endOffset))
        assertEquals("zoneless", file.text.substring(duplicate.startOffset, duplicate.endOffset))
    }

    private companion object {
        const val DOLLAR = '$'
    }
}
