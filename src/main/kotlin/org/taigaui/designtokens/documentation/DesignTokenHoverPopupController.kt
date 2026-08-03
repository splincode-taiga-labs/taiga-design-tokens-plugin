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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
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
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.nio.file.Path
import javax.swing.JComponent
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
    private val nativeHoverPopupSuppression = DesignTokenNativeHoverPopupSuppression()
    private var popup: JBPopup? = null
    private var popupContent: JComponent? = null
    private var popupKey: PopupKey? = null

    init {
        coroutineScope.launch(CoroutineName("Taiga UI design token hover popup")) {
            requests
                .debounce(HOVER_DELAY)
                .collectLatest(::handleRequest)
        }
    }

    fun mouseMoved(event: EditorMouseEvent) {
        val editor = event.editor

        if (editor.project == project && !editor.isDisposed) {
            val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)
            val isDesignTokenReference =
                virtualFile?.extension?.lowercase() in SUPPORTED_EXTENSIONS &&
                    DesignTokenReferenceAtOffsetFinder.find(
                        editor.document.immutableCharSequence,
                        event.offset,
                    ) != null

            if (isDesignTokenReference) {
                nativeHoverPopupSuppression.suppress(editor)
            }

            requests.tryEmit(
                HoverRequest(
                    editor = editor,
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
        val virtualFile =
            takeIf { !project.isDisposed && !editor.isDisposed }
                ?.let { FileDocumentManager.getInstance().getFile(editor.document) }
                ?.takeIf { file -> file.extension?.lowercase() in SUPPORTED_EXTENSIONS }
        val reference =
            virtualFile?.let {
                DesignTokenReferenceAtOffsetFinder.find(
                    editor.document.immutableCharSequence,
                    offset,
                )
            }
        val sourceFile =
            virtualFile?.let { file ->
                runCatching { Path.of(file.path) }.getOrNull()
            }

        return if (reference == null || sourceFile == null) {
            null
        } else {
            project
                .service<DesignTokenIndexService>()
                .resolveToken(sourceFile, reference.name)
                .takeIf { groups -> groups.isNotEmpty() }
                ?.let { groups ->
                    PopupData(
                        key = PopupKey(editor, reference.name),
                        model = DesignTokenHoverPopupModel.create(reference.name, groups),
                    )
                }
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
        nativeHoverPopupSuppression.suppress(editor)

        val popupWidth = calculatePopupWidth(editor)
        var popupReference: JBPopup? = null
        val panel =
            DesignTokenHoverPopupPanel(
                model = data.model,
                popupWidth = popupWidth,
                onNavigate = { navigateToDefinition(data.model.navigationTarget) },
                onReportBug = ::reportBug,
                onPreferredSizeChanged = { size ->
                    popupReference
                        ?.takeIf { currentPopup -> currentPopup.isVisible && !currentPopup.isDisposed }
                        ?.let { currentPopup ->
                            currentPopup.setSize(size)
                            currentPopup.moveToFitScreen()
                        }
                },
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
                .setMinSize(
                    Dimension(
                        minOf(popupWidth, JBUI.scale(MIN_POPUP_WIDTH)),
                        JBUI.scale(MIN_POPUP_HEIGHT),
                    ),
                ).createPopup()

        popupReference = createdPopup
        createdPopup.addListener(
            object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    if (popup === createdPopup) {
                        popup = null
                        popupContent = null
                        popupKey = null
                        nativeHoverPopupSuppression.restore()
                    }
                }
            },
        )
        popup = createdPopup
        popupContent = panel
        popupKey = data.key
        createdPopup.show(
            RelativePoint(
                editor.contentComponent,
                Point(anchor.x + ANCHOR_X_OFFSET, anchor.y + editor.lineHeight + ANCHOR_Y_OFFSET),
            ),
        )
        createdPopup.moveToFitScreen()
    }

    private fun calculatePopupWidth(editor: Editor): Int {
        val preferredWidth = JBUI.scale(PREFERRED_POPUP_WIDTH)
        val minimumWidth = JBUI.scale(MIN_POPUP_WIDTH)
        val availableWidth =
            editor.contentComponent.graphicsConfiguration
                ?.bounds
                ?.width
                ?.let { screenWidth -> (screenWidth * MAX_SCREEN_WIDTH_RATIO).toInt() }
                ?: preferredWidth

        return preferredWidth
            .coerceAtMost(availableWidth)
            .coerceAtLeast(minimumWidth.coerceAtMost(availableWidth))
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
        val content = popupContent?.takeIf(JComponent::isShowing)
        val pointer = MouseInfo.getPointerInfo()?.location

        return if (content == null || pointer == null) {
            false
        } else {
            SwingUtilities.convertPointFromScreen(pointer, content)
            content.contains(pointer)
        }
    }

    private fun hidePopup() {
        val currentPopup = popup

        popup = null
        popupContent = null
        popupKey = null
        currentPopup?.cancel()
        nativeHoverPopupSuppression.restore()
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
        const val PREFERRED_POPUP_WIDTH = 560
        const val MIN_POPUP_WIDTH = 460
        const val MIN_POPUP_HEIGHT = 210
        const val MAX_SCREEN_WIDTH_RATIO = 0.72
        const val ANCHOR_X_OFFSET = 14
        const val ANCHOR_Y_OFFSET = 8
        const val REPORT_BUG_URL =
            "https://github.com/taiga-family-labs/taiga-design-tokens-plugin/issues/new?labels=bug"
    }
}
