package org.taigaui.designtokens.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPluginBindingTest {
    @Test
    fun `parses composed event modifiers`() {
        val binding = requireNotNull(EventPluginBinding.parse("(click.zoneless.capture)"))

        assertEquals("click", binding.event)
        assertEquals(listOf("zoneless", "capture"), binding.modifiers.map(EventPluginModifier::source))
    }

    @Test
    fun `keeps angular pseudo event segments in base event`() {
        val binding = requireNotNull(EventPluginBinding.parse("(keydown.enter.stop)"))

        assertEquals("keydown.enter", binding.event)
        assertEquals(listOf("stop"), binding.modifiers.map(EventPluginModifier::source))
    }

    @Test
    fun `supports timed modifiers`() {
        val binding = requireNotNull(EventPluginBinding.parse("(click.debounce~300ms.once)"))

        assertEquals(listOf("debounce~300ms", "once"), binding.modifiers.map(EventPluginModifier::source))
    }

    @Test
    fun `rejects unknown and duplicate taiga modifiers`() {
        assertNull(EventPluginBinding.parse("(click.captre)"))
        assertNull(EventPluginBinding.parse("(click.zoneless.captre)"))
        assertNull(EventPluginBinding.parse("(click.zoneless.zoneless)"))
    }

    @Test
    fun `ignores bindings without taiga modifiers`() {
        assertNull(EventPluginBinding.parse("(click)"))
        assertNull(EventPluginBinding.parse("class"))
    }

    @Test
    fun `combines modifier behavior`() {
        val binding = requireNotNull(EventPluginBinding.parse("(click.zoneless.capture)"))

        assertTrue(binding.combinedBehavior.contains("Handles click"))
        assertTrue(binding.combinedBehavior.contains("outside Angular's NgZone"))
        assertTrue(binding.combinedBehavior.contains("capture phase"))
    }

    @Test
    fun `finds binding under pointer`() {
        val text = """<button (click.zoneless.capture)="method()"></button>"""
        val offset = text.indexOf("zoneless") + 2
        val reference = requireNotNull(EventPluginBindingAtOffsetFinder.find(text, offset))

        assertEquals("(click.zoneless.capture)", reference.binding.source)
        assertEquals("click", reference.binding.event)
        assertEquals(text.indexOf("(click"), reference.startOffset)
        assertEquals(text.indexOf(")=\"") + 1, reference.endOffset)
    }

    @Test
    fun `finder requires angular attribute assignment`() {
        val text = "const value = (click.stop)"
        val offset = text.indexOf("stop")

        assertNull(EventPluginBindingAtOffsetFinder.find(text, offset))
    }
}
