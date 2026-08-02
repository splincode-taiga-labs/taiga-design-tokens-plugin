package org.taigaui.designtokens.documentation

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.taigaui.designtokens.project.DesignTokenIndexService
import java.awt.MouseInfo
import java.awt.Point
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
internal class DesignTokenHoverPopupController(
    private val project: Project,
    coroutineScope: CoroutineScope,
) {
    private val requests =
        MutableSharedFlow<HoverRequest>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private var popup: JBPopup? = null
    private var popupKey: PopupKey? = null

    init {
        coroutineScope.launch(CoroutineName("Taiga UI design token hover popup")) {
            requests
                .debounce(HOVER_DELAY)
                .collectLatest(::handleRequest)
        }
    }

    fun mouseMoved(event: EditorMouseEvent) {
        if (event.editor.project == project && !event.editor.isDisposed) {
            requests.tryEmit(
                HoverRequest(
                    editor = event.editor,
                    offset = event.offset,
                    anchor = Point(event.mouseEvent.point),
                ),
            )
        }
    }

    private suspend fun handleRequest(request: HoverRequest) {
        val popupData = readAction { request.resolvePopupData() }

        withContext(Dispatchers.EDT) {
            if (popupData == null) {
                hidePopupIfPointerOutside()
            } else {
                showPopup(request.editor, request.anchor, popupData)
            }
        }
    }

    private fun HoverRequest.resolvePopupData(): PopupData? {
        if (project.isDisposed || editor.isDisposed) {
            return null
        }

        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return null

        if (virtualFile.extension?.lowercase() !in SUPPORTED_EXTENSIONS) {
            return null
        }

        val reference =
            DesignTokenReferenceAtOffsetFinder.find(
                editor.document.immutableCharSequence,
                offset,
            ) ?: return null
        val sourceFile = runCatching { Path.of(virtualFile.path) }.getOrNull() ?: return null
        val groups = project.service<DesignTokenIndexService>().resolveToken(sourceFile, reference.name)

        return groups
            .takeIf(List<*>::isNotEmpty)
            ?.let { resolutions ->
                PopupData(
                    key = PopupKey(editor, reference.name),
                    model = DesignTokenHoverPopupModel.create(reference.name, resolutions),
                )
            }
    }

    private fun showPopup(
        editor: Editor,
        anchor: Point,
        data: PopupData,
    ) {
        if (popupKey == data.key && popup?.isVisible == true) {
            return
        }

        hidePopup()

        val panel =
            DesignTokenHoverPopupPanel(
                model = data.model,
                onCopy = { copyValue(data.model.copyValue) },
                onNavigate = { navigateToDefinition(data.model.navigationTarget) },
                onReportBug = ::reportBug,
            )
        val createdPopup =
            JBPopupFactory
                .getInstance()
                .createComponentPopupBuilder(panel, panel)
                .setProject(project)
                .setRequestFocus(false)
                .setFocusable(true)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .setCancelOnWindowDeactivation(true)
                .setCancelKeyEnabled(true)
                .setMovable(false)
                .setResizable(true)
                .setMinSize(JBUI.size(MIN_POPUP_WIDTH, MIN_POPUP_HEIGHT))
                .createPopup()

        popup = createdPopup
        popupKey = data.key
        createdPopup.show(
            RelativePoint(
                editor.contentComponent,
                Point(anchor.x + ANCHOR_X_OFFSET, anchor.y + editor.lineHeight + ANCHOR_Y_OFFSET),
            ),
        )
    }

    private fun copyValue(value: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(value))
    }

    private fun navigateToDefinition(target: DesignTokenNavigationTarget?) {
        val validTarget = target ?: return
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(validTarget.sourceFile) ?: return

        OpenFileDescriptor(
            project,
            file,
            (validTarget.line - 1).coerceAtLeast(0),
            0,
        ).navigate(true)
        hidePopup()
    }

    private fun reportBug() {
        BrowserUtil.browse(REPORT_BUG_URL)
    }

    private fun hidePopupIfPointerOutside() {
        if (!isPointerInsidePopup()) {
            hidePopup()
        }
    }

    private fun isPointerInsidePopup(): Boolean {
        val content = popup?.content?.takeIf { component -> component.isShowing } ?: return false
        val pointer = MouseInfo.getPointerInfo()?.location ?: return false

        SwingUtilities.convertPointFromScreen(pointer, content)

        return content.contains(pointer)
    }

    private fun hidePopup() {
        popup?.cancel()
        popup = null
        popupKey = null
    }

    private data class HoverRequest(
        val editor: Editor,
        val offset: Int,
        val anchor: Point,
    )

    private data class PopupKey(
        val editor: Editor,
        val tokenName: String,
    )

    private data class PopupData(
        val key: PopupKey,
        val model: DesignTokenHoverPopupModel,
    )

    private companion object {
        val HOVER_DELAY = 350.milliseconds
        val SUPPORTED_EXTENSIONS = setOf("css", "less", "scss")
        const val MIN_POPUP_WIDTH = 720
        const val MIN_POPUP_HEIGHT = 300
        const val ANCHOR_X_OFFSET = 14
        const val ANCHOR_Y_OFFSET = 8
        const val REPORT_BUG_URL =
            "https://github.com/taiga-family-labs/taiga-design-tokens-plugin/issues/new?labels=bug"
    }
}
