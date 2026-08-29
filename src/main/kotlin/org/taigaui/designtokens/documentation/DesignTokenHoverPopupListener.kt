package org.taigaui.designtokens.documentation

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import org.taigaui.designtokens.units.RemHoverPopupController

internal class DesignTokenHoverPopupListener :
    EditorMouseListener,
    EditorMouseMotionListener {
    override fun mousePressed(event: EditorMouseEvent) {
        event.dismissStylesheetHoverPopups()
    }

    override fun mouseDragged(event: EditorMouseEvent) {
        event.dismissStylesheetHoverPopups()
    }

    override fun mouseMoved(event: EditorMouseEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        val remController = project.service<RemHoverPopupController>()
        val tokenController = project.service<DesignTokenHoverPopupController>()

        if (LookupManager.getInstance(project).activeLookup != null) {
            remController.dismissHover(editor)
            tokenController.dismissHover(editor)
            return
        }

        if (remController.canHandle(event)) {
            tokenController.dismissHover(editor)
            remController.mouseMoved(event)
        } else {
            remController.dismissHover(editor)
            tokenController.mouseMoved(event)
        }
    }
}

private fun EditorMouseEvent.dismissStylesheetHoverPopups() {
    val project = editor.project ?: return

    project.service<DesignTokenHoverPopupController>().dismissHover(editor)
    project.service<RemHoverPopupController>().dismissHover(editor)
}
