package org.taigaui.designtokens.documentation

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener

internal class DesignTokenHoverPopupListener : EditorMouseMotionListener {
    override fun mouseMoved(event: EditorMouseEvent) {
        event.editor.project
            ?.service<DesignTokenHoverPopupController>()
            ?.mouseMoved(event)
    }
}
