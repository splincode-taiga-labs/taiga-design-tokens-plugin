package org.taigaui.designtokens.icons

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.taigaui.designtokens.documentation.DesignTokenReferenceHitTester
import java.awt.Image
import java.awt.Point
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
internal class IconHoverPopupController(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val renderer = IconSvgPreviewRenderer()
    private var activeKey: IconHoverKey? = null
    private var hoverJob: Job? = null
    private var popup: JBPopup? = null

    fun mouseMoved(event: EditorMouseEvent) {
        val request = event.toIconHoverRequest(project)
        val requestKey = request?.key

        if (requestKey == null) {
            dismissHover(event.editor)
        } else if (requestKey != activeKey) {
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
        }
    }

    private fun scheduleHover(request: IconHoverRequest): Job =
        coroutineScope.launch(CoroutineName("Taiga UI icon hover preview")) {
            delay(ICON_HOVER_DELAY)

            if (!request.isStillCurrent()) {
                clearIfCurrent(request.key)
                return@launch
            }

            val image =
                withContext(Dispatchers.IO) {
                    val service = project.service<IconCompletionService>()

                    service.loadNow(request.sourceFile)
                    service
                        .svgSourceFor(request.sourceFile, request.reference.name)
                        ?.let { source -> renderer.render(source, ICON_PREVIEW_LOGICAL_SIZE) }
                }

            withContext(Dispatchers.EDT) {
                if (image == null) {
                    clearIfCurrent(request.key)
                } else if (request.isStillCurrent() && activeKey == request.key) {
                    showPopup(request, image)
                }
            }
        }

    private fun showPopup(
        request: IconHoverRequest,
        image: Image,
    ) {
        if (popup?.isVisible == true || JBPopupFactory.getInstance().isPopupActive) {
            return
        }

        val panel = IconCompletionPreviewPanel().apply { showIcon(request.reference.name, image) }
        val createdPopup =
            JBPopupFactory
                .getInstance()
                .createComponentPopupBuilder(panel, panel)
                .setProject(project)
                .setRequestFocus(false)
                .setFocusable(false)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .setCancelOnWindowDeactivation(true)
                .setMovable(false)
                .setResizable(false)
                .createPopup()

        popup = createdPopup
        createdPopup.showInScreenCoordinates(
            request.editor.contentComponent,
            request.popupLocation(),
        )
        createdPopup.moveToFitScreen()
    }

    private fun clearIfCurrent(key: IconHoverKey) {
        if (activeKey == key) {
            activeKey = null
            hoverJob = null
            hidePopup()
        }
    }

    private fun hidePopup() {
        val currentPopup = popup

        popup = null
        currentPopup?.cancel()
    }
}

private fun EditorMouseEvent.toIconHoverRequest(project: Project): IconHoverRequest? {
    val sourceFile = editor.iconSourceFile() ?: return null
    val reference =
        takeIf { area == EditorMouseEventArea.EDITING_AREA }
            ?.let { IconReferenceAtOffsetFinder.find(editor.document.immutableCharSequence, offset) }
            ?.takeIf { current -> editor.isPointerOver(current, mouseEvent.point) }
            ?: return null

    if (
        editor.project != project ||
        editor.isDisposed ||
        editor.selectionModel.hasSelection() ||
        LookupManager.getInstance(project).activeLookup != null
    ) {
        return null
    }

    return IconHoverRequest(
        editor = editor,
        sourceFile = sourceFile,
        reference = reference,
        anchor = Point(mouseEvent.point),
        modificationStamp = editor.document.modificationStamp,
    )
}

private fun IconHoverRequest.isStillCurrent(): Boolean =
    editor.project == editor.project &&
        !editor.isDisposed &&
        !editor.selectionModel.hasSelection() &&
        editor.document.modificationStamp == modificationStamp &&
        LookupManager.getInstance(requireNotNull(editor.project)).activeLookup == null

private fun Editor.iconSourceFile(): Path? =
    FileDocumentManager
        .getInstance()
        .getFile(document)
        ?.takeIf { file -> file.extension?.lowercase() in ICON_SUPPORTED_EXTENSIONS }
        ?.path
        ?.let { path -> runCatching { Path.of(path) }.getOrNull() }

private fun Editor.isPointerOver(
    reference: IconReferenceAtOffset,
    pointer: Point,
): Boolean =
    DesignTokenReferenceHitTester.contains(
        start = offsetToXY(reference.startOffset),
        end = offsetToXY(reference.endOffset),
        lineHeight = lineHeight,
        pointer = pointer,
    )

private fun IconHoverRequest.popupLocation(): Point {
    val point = Point(anchor)

    SwingUtilities.convertPointToScreen(point, editor.contentComponent)

    return Point(point.x, point.y + editor.lineHeight)
}

private val IconHoverRequest.key: IconHoverKey
    get() =
        IconHoverKey(
            editor = editor,
            iconName = reference.name,
            startOffset = reference.startOffset,
            modificationStamp = modificationStamp,
        )

private data class IconHoverRequest(
    val editor: Editor,
    val sourceFile: Path,
    val reference: IconReferenceAtOffset,
    val anchor: Point,
    val modificationStamp: Long,
)

private data class IconHoverKey(
    val editor: Editor,
    val iconName: String,
    val startOffset: Int,
    val modificationStamp: Long,
)

private val ICON_HOVER_DELAY = 2.seconds
