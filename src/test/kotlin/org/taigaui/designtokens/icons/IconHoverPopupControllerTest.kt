package org.taigaui.designtokens.icons

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndGet
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path

class IconHoverPopupControllerTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("icon-hover-controller")
    }

    override fun tearDown() {
        try {
            tempRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testValidIconHoverStartsRequestAndDismissRestoresState() {
        configureHtml("""<button iconStart="@tui.search"></button>""")
        val editor = myFixture.editor
        val controller = project.service<IconHoverPopupController>()
        val offset = editor.document.text.indexOf("@tui.search") + 3

        controller.mouseMoved(editorMouseEvent(offset))

        assertNotNull(waitForPrivateField(controller, "activeKey"))
        assertNotNull(readPrivateField(controller, "hoverJob"))
        assertSame(editor, readPrivateField(controller, "nativeHoverSuppressedEditor"))

        controller.dismissHover(editor)

        assertNull(readPrivateField(controller, "activeKey"))
        assertNull(readPrivateField(controller, "hoverJob"))
        assertNull(readPrivateField(controller, "nativeHoverSuppressedEditor"))
    }

    fun testRepeatedSameHoverKeepsSingleActiveKey() {
        configureHtml("""<button iconStart="@tui.search"></button>""")
        val editor = myFixture.editor
        val controller = project.service<IconHoverPopupController>()
        val offset = editor.document.text.indexOf("@tui.search") + 3

        controller.mouseMoved(editorMouseEvent(offset))
        val key = requireNotNull(waitForPrivateField(controller, "activeKey"))
        val job = requireNotNull(readPrivateField(controller, "hoverJob"))

        controller.mouseMoved(editorMouseEvent(offset))

        assertSame(key, readPrivateField(controller, "activeKey"))
        assertSame(job, readPrivateField(controller, "hoverJob"))

        controller.dismissHover()
    }

    fun testNonEditingAreaAndSelectionDoNotStartIconHover() {
        configureHtml("""<button iconStart="@tui.search"></button>""")
        val editor = myFixture.editor
        val controller = project.service<IconHoverPopupController>()
        val offset = editor.document.text.indexOf("@tui.search") + 3

        controller.mouseMoved(
            editorMouseEvent(
                offset,
                EditorMouseEventArea.LINE_NUMBERS_AREA,
            ),
        )
        assertNull(readPrivateField(controller, "activeKey"))

        editor.selectionModel.setSelection(0, 1)
        controller.mouseMoved(editorMouseEvent(offset))

        assertNull(readPrivateField(controller, "activeKey"))
    }

    fun testUnsupportedSourceExtensionDoesNotStartIconHover() {
        configureSource(
            fileName = "icons.css",
            content = """.demo { content: "@tui.search"; }""",
        )
        val controller = project.service<IconHoverPopupController>()
        val offset =
            myFixture.editor.document.text
                .indexOf("@tui.search") + 3

        controller.mouseMoved(editorMouseEvent(offset))

        assertNull(readPrivateField(controller, "activeKey"))
    }

    private fun configureHtml(content: String) {
        configureSource("icons.html", content)
    }

    private fun configureSource(
        fileName: String,
        content: String,
    ) {
        val path = tempRoot.resolve(fileName)
        Files.writeString(path, content)
        val file =
            requireNotNull(
                LocalFileSystem
                    .getInstance()
                    .refreshAndFindFileByNioFile(path),
            )

        myFixture.configureFromExistingVirtualFile(file)
    }

    private fun editorMouseEvent(
        offset: Int,
        area: EditorMouseEventArea = EditorMouseEventArea.EDITING_AREA,
    ): EditorMouseEvent {
        val editor = myFixture.editor
        val point = editor.offsetToXY(offset)
        val mouseEvent =
            MouseEvent(
                editor.contentComponent,
                MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(),
                0,
                point.x,
                point.y,
                0,
                false,
            )

        return EditorMouseEvent(editor, mouseEvent, area)
    }

    private fun readPrivateField(
        target: Any,
        fieldName: String,
    ): Any? {
        val field =
            target.javaClass
                .getDeclaredField(fieldName)
                .apply { isAccessible = true }

        return runInEdtAndGet { field.get(target) }
    }

    private fun waitForPrivateField(
        target: Any,
        fieldName: String,
    ): Any? {
        repeat(200) {
            readPrivateField(target, fieldName)?.let { return it }
            Thread.sleep(10)
        }

        return readPrivateField(target, fieldName)
    }
}
