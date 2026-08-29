package org.taigaui.designtokens.units

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RemDocumentationTargetProviderTest : BasePlatformTestCase() {
    fun testProvidesRemDocumentationForCssValue() {
        val text = ".button { min-width: 21rem; }"
        val psiFile = myFixture.configureByText("styles.css", text)
        val offset = text.indexOf("21rem") + 2

        val target =
            RemDocumentationTargetProvider()
                .documentationTargets(psiFile, offset)
                .single()

        assertEquals("21rem = 336px", target.computeDocumentationHint())
    }

    fun testProvidesRemDocumentationForLessAndScss() {
        listOf("less", "scss").forEach { extension ->
            val text = ".button { gap: 1.5rem; }"
            val psiFile = myFixture.configureByText("styles.$extension", text)
            val offset = text.indexOf("1.5rem") + 2

            val target =
                RemDocumentationTargetProvider()
                    .documentationTargets(psiFile, offset)
                    .single()

            assertEquals("1.5rem = 24px", target.computeDocumentationHint())
        }
    }

    fun testDoesNotProvideRemDocumentationOutsideStylesheets() {
        val text = "const size = '1rem';"
        val psiFile = myFixture.configureByText("example.ts", text)

        assertEmpty(
            RemDocumentationTargetProvider().documentationTargets(
                psiFile,
                text.indexOf("1rem") + 1,
            ),
        )
    }
}
