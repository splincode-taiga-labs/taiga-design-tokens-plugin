package org.taigaui.designtokens.completion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesignTokenCompletionContextFinderTest {
    @Test
    fun `finds taiga token prefix in first var argument`() {
        assertPrefix(
            ".demo { color: var(--tui-te<caret>); }",
            "--tui-te",
        )
    }

    @Test
    fun `allows whitespace before token prefix`() {
        assertPrefix(
            ".demo { color: var(  --tui-<caret>); }",
            "--tui-",
        )
    }

    @Test
    fun `finds nested var first argument`() {
        assertPrefix(
            ".demo { color: var(--brand, var(--tui-text-<caret>)); }",
            "--tui-text-",
        )
    }

    @Test
    fun `ignores second var argument`() {
        assertNoContext(".demo { color: var(--brand, --tui-te<caret>); }")
    }

    @Test
    fun `ignores token declarations`() {
        assertNoContext(":root { --tui-te<caret>: red; }")
    }

    @Test
    fun `ignores non taiga custom properties`() {
        assertNoContext(".demo { color: var(--brand-te<caret>); }")
    }

    @Test
    fun `ignores var text inside comments`() {
        assertNoContext(".demo { /* var(--tui-te<caret>) */ color: red; }")
    }

    @Test
    fun `ignores var text inside strings`() {
        assertNoContext(".demo::before { content: 'var(--tui-te<caret>)'; }")
    }

    private fun assertPrefix(
        source: String,
        expectedPrefix: String,
    ) {
        val (text, offset) = source.withCaret()

        assertEquals(
            expectedPrefix,
            DesignTokenCompletionContextFinder.find(text, offset)?.prefix,
        )
    }

    private fun assertNoContext(source: String) {
        val (text, offset) = source.withCaret()

        assertNull(DesignTokenCompletionContextFinder.find(text, offset))
    }

    private fun String.withCaret(): Pair<String, Int> {
        val offset = indexOf(CARET)

        require(offset >= 0)

        return replace(CARET, "") to offset
    }

    private companion object {
        const val CARET = "<caret>"
    }
}
