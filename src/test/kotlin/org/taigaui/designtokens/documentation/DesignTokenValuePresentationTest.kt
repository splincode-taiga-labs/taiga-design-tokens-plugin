package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignTokenValuePresentationTest {
    @Test
    fun `shows pixel equivalent for exact rem values`() {
        assertEquals("1rem · 16px", designTokenValuePresentation("1rem"))
        assertEquals("1.25rem · 20px", designTokenValuePresentation("1.25rem"))
        assertEquals("0.5rem · 8px", designTokenValuePresentation("0.5rem"))
    }

    @Test
    fun `keeps non exact rem values unchanged`() {
        assertEquals("40px", designTokenValuePresentation("40px"))
        assertEquals("300ms", designTokenValuePresentation("300ms"))
        assertEquals("calc(1rem + 2px)", designTokenValuePresentation("calc(1rem + 2px)"))
    }
}
