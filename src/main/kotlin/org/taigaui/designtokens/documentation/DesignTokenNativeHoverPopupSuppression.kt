package org.taigaui.designtokens.documentation

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl

internal class DesignTokenNativeHoverPopupSuppression {
    private var editor: Editor? = null

    fun suppress(editor: Editor) {
        update(editor)
    }

    fun restore() {
        update(null)
    }

    private fun update(nextEditor: Editor?) {
        if (editor === nextEditor) {
            return
        }

        editor
            ?.takeUnless(Editor::isDisposed)
            ?.let { previousEditor -> EditorMouseHoverPopupControl.enablePopups(previousEditor) }
        editor = nextEditor?.takeUnless(Editor::isDisposed)
        editor?.let { currentEditor -> EditorMouseHoverPopupControl.disablePopups(currentEditor) }
    }
}
