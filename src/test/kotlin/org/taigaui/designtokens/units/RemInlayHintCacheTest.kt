package org.taigaui.designtokens.units

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RemInlayHintCacheTest : BasePlatformTestCase() {
    fun testReusesHintsForUnchangedLargeStylesheetAndRefreshesAfterEdit() {
        val content =
            buildString {
                repeat(LARGE_DECLARATION_COUNT) { index ->
                    append(".item-$index { margin: 1rem; }\n")
                }
            }
        val file = myFixture.configureByText("large.css", content)
        val cache = project.service<RemInlayHintCache>()
        var scans = 0

        fun hints(): List<RemInlayHint> =
            cache.hints(file, RemInlayHintKind.STYLESHEET) {
                scans++
                RemInlayHintCollector.collect(file.text)
            }

        assertEquals(LARGE_DECLARATION_COUNT, hints().size)
        assertEquals(LARGE_DECLARATION_COUNT, hints().size)
        assertEquals(1, scans)

        val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
        val valueOffset = document.text.indexOf("1rem")

        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(valueOffset, valueOffset + "1rem".length, "2rem")
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)

        val updatedHints = hints()

        assertEquals(LARGE_DECLARATION_COUNT, updatedHints.size)
        assertEquals(" 32px", updatedHints.first().text)
        assertEquals(2, scans)
    }

    fun testKeepsIndependentEntriesForDifferentHintKinds() {
        val file = myFixture.configureByText("component.html", """<div [style.gap.rem]="1"></div>""")
        val cache = project.service<RemInlayHintCache>()
        var stylesheetScans = 0
        var templateScans = 0

        cache.hints(file, RemInlayHintKind.STYLESHEET) {
            stylesheetScans++
            emptyList()
        }
        cache.hints(file, RemInlayHintKind.ANGULAR_TEMPLATE) {
            templateScans++
            AngularRemStyleBindingHintCollector.collect(file.text)
        }
        cache.hints(file, RemInlayHintKind.STYLESHEET) {
            stylesheetScans++
            emptyList()
        }
        cache.hints(file, RemInlayHintKind.ANGULAR_TEMPLATE) {
            templateScans++
            AngularRemStyleBindingHintCollector.collect(file.text)
        }

        assertEquals(1, stylesheetScans)
        assertEquals(1, templateScans)
    }

    private companion object {
        const val LARGE_DECLARATION_COUNT = 2_000
    }
}
