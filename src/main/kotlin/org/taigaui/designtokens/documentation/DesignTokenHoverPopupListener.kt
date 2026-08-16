package org.taigaui.designtokens.documentation

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener

internal class DesignTokenHoverPopupListener :
    EditorMouseListener,
    EditorMouseMotionListener {
    override fun mousePressed(event: EditorMouseEvent) {
        event.dismissDesignTokenHoverPopup()
    }

    override fun mouseDragged(event: EditorMouseEvent) {
        event.dismissDesignTokenHoverPopup()
    }

    override fun mouseMoved(event: EditorMouseEvent) {
        val editor = event.editor
        val project = editor.project ?: return

        if (LookupManager.getInstance(project).activeLookup == null) {
            project.service<DesignTokenHoverPopupController>().mouseMoved(event)
        }
    }
}

private fun EditorMouseEvent.dismissDesignTokenHoverPopup() {
    val project = editor.project ?: return

    project.service<DesignTokenHoverPopupController>().editorInteraction(editor)
}
