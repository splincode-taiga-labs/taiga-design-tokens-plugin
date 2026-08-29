package org.taigaui.designtokens.units

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.taigaui.designtokens.documentation.DesignTokenReferenceHitTester
import java.awt.Point
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
internal class RemHoverPopupController(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private var activeKey: RemHoverKey? = null
    private var hoverJob: Job? = null
    private var popup: JBPopup? = null
    private var nativeHoverSuppressedEditor: Editor? = null

    fun canHandle(event: EditorMouseEvent): Boolean = event.toRemHoverRequest(project) != null

    fun mouseMoved(event: EditorMouseEvent) {
        val request = event.toRemHoverRequest(project)
        val requestKey = request?.key

        if (requestKey == null) {
            dismissHover(event.editor)
            return
        }

        suppressNativeHover(request.editor)

        if (requestKey != activeKey) {
            activeKey = requestKey
            hoverJob?.cancel()
            hidePopup()
            hoverJob = scheduleHover(request)
        }
    }

    fun dismissHover(editor: Editor? = null) {
        if (editor == null || editor.project == project) {
            activeKey = null
            hoverJob?.cancel()
            hoverJob = null
            hidePopup()
            restoreNativeHover()
        }
    }

    private fun scheduleHover(request: RemHoverRequest): Job =
        coroutineScope.launch(Dispatchers.EDT + CoroutineName("Taiga UI rem hover preview")) {
            delay(REM_HOVER_DELAY)

            if (!request.isStillCurrent(project) || activeKey != request.key) {
                clearIfCurrent(request.key)
                return@launch
            }

            showPopup(request)
        }

    private fun showPopup(request: RemHoverRequest) {
        if (popup?.isVisible == true || JBPopupFactory.getInstance().isPopupActive) {
            return
        }

        val label =
            JBLabel(request.reference.presentation()).apply {
                border = JBUI.Borders.empty(8, 10)
            }
        val createdPopup =
            JBPopupFactory
                .getInstance()
                .createComponentPopupBuilder(label, label)
                .setProject(project)
                .setRequestFocus(false)
                .setFocusable(false)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .setCancelOnWindowDeactivation(true)
                .setMovable(false)
                .setResizable(false)
                .createPopup()

        createdPopup.addListener(
            object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    if (popup === createdPopup) {
                        popup = null
                        activeKey = null
                        hoverJob = null
                        restoreNativeHover()
                    }
                }
            },
        )
        popup = createdPopup
        createdPopup.showInScreenCoordinates(
            request.editor.contentComponent,
            request.popupLocation(),
        )
        createdPopup.moveToFitScreen()
    }

    private fun clearIfCurrent(key: RemHoverKey) {
        if (activeKey == key) {
            activeKey = null
            hoverJob = null
            hidePopup()
            restoreNativeHover()
        }
    }

    private fun suppressNativeHover(editor: Editor) {
        if (nativeHoverSuppressedEditor === editor) {
            return
        }

        restoreNativeHover()
        EditorMouseHoverPopupControl.disablePopups(editor)
        nativeHoverSuppressedEditor = editor
    }

    private fun restoreNativeHover() {
        val editor = nativeHoverSuppressedEditor ?: return

        nativeHoverSuppressedEditor = null

        if (!editor.isDisposed) {
            EditorMouseHoverPopupControl.enablePopups(editor)
        }
    }

    private fun hidePopup() {
        val currentPopup = popup

        popup = null
        currentPopup?.cancel()
    }
}

private fun EditorMouseEvent.toRemHoverRequest(project: Project): RemHoverRequest? =
    takeIf { area == EditorMouseEventArea.EDITING_AREA }
        ?.takeIf { editor.canShowRemHover(project) }
        ?.takeIf { editor.isSupportedStylesheet() }
        ?.let { event ->
            RemValueAtOffsetFinder
                .find(editor.document.immutableCharSequence, offset)
                ?.takeIf { reference -> editor.isPointerOver(reference, mouseEvent.point) }
                ?.let { reference ->
                    RemHoverRequest(
                        editor = editor,
                        reference = reference,
                        anchor = Point(event.mouseEvent.point),
                        modificationStamp = editor.document.modificationStamp,
                    )
                }
        }

private fun Editor.canShowRemHover(project: Project): Boolean =
    this.project == project &&
        !project.isDisposed &&
        !isDisposed &&
        !selectionModel.hasSelection() &&
        LookupManager.getInstance(project).activeLookup == null

private fun Editor.isSupportedStylesheet(): Boolean =
    FileDocumentManager
        .getInstance()
        .getFile(document)
        ?.extension
        ?.lowercase() in REM_SUPPORTED_EXTENSIONS

private fun Editor.isPointerOver(
    reference: RemValueAtOffset,
    pointer: Point,
): Boolean =
    DesignTokenReferenceHitTester.contains(
        start = offsetToXY(reference.startOffset),
        end = offsetToXY(reference.endOffset),
        lineHeight = lineHeight,
        pointer = pointer,
    )

private fun RemHoverRequest.isStillCurrent(project: Project): Boolean =
    editor.canShowRemHover(project) &&
        editor.document.modificationStamp == modificationStamp

private fun RemHoverRequest.popupLocation(): Point {
    val point = Point(anchor)

    SwingUtilities.convertPointToScreen(point, editor.contentComponent)

    return Point(point.x, point.y + editor.lineHeight)
}

private val RemHoverRequest.key: RemHoverKey
    get() =
        RemHoverKey(
            editor = editor,
            startOffset = reference.startOffset,
            modificationStamp = modificationStamp,
        )

private data class RemHoverRequest(
    val editor: Editor,
    val reference: RemValueAtOffset,
    val anchor: Point,
    val modificationStamp: Long,
)

private data class RemHoverKey(
    val editor: Editor,
    val startOffset: Int,
    val modificationStamp: Long,
)

private val REM_HOVER_DELAY = 500.milliseconds
private val REM_SUPPORTED_EXTENSIONS = setOf("css", "less", "scss")
