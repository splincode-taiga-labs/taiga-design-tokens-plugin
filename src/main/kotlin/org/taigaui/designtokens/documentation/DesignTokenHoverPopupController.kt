package org.taigaui.designtokens.documentation

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.taigaui.designtokens.project.DesignTokenIndexService
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.nio.file.Path
import javax.swing.SwingUtilities

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
    private var popupContent: DesignTokenHoverPopupPanel? = null
    private var popupKey: PopupKey? = null
    private var activeHoverKey: PopupKey? = null

    init {
        coroutineScope.launch(CoroutineName("Taiga UI design token hover popup")) {
            requests.collectLatest(::handleRequest)
        }
    }

    fun mouseMoved(event: EditorMouseEvent) {
        val editor = event.editor

        if (editor.project == project && !editor.isDisposed) {
            val anchor = Point(event.mouseEvent.point)
            val reference = event.findReferenceUnderPointer(anchor)

            if (reference != null) {
                nativeHoverPopupSuppression.suppress(editor)
            }

            val request =
                HoverRequest(
                    editor = editor,
                    reference = reference,
                    anchor = anchor,
                    modificationStamp = editor.document.modificationStamp,
                )
            val requestKey = request.popupKey()

            if (requestKey != activeHoverKey) {
                activeHoverKey = requestKey
                requests.tryEmit(request)
            }
        }
    }

    private fun EditorMouseEvent.findReferenceUnderPointer(anchor: Point): DesignTokenReferenceAtOffset? {
        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)

        return takeIf { area == EditorMouseEventArea.EDITING_AREA }
            ?.takeIf { virtualFile?.extension?.lowercase() in SUPPORTED_EXTENSIONS }
            ?.let {
                DesignTokenReferenceAtOffsetFinder.find(
                    editor.document.immutableCharSequence,
                    offset,
                )
            }?.takeIf { reference -> editor.isPointerOver(reference, anchor) }
    }

    private fun Editor.isPointerOver(
        reference: DesignTokenReferenceAtOffset,
        pointer: Point,
    ): Boolean =
        DesignTokenReferenceHitTester.contains(
            start = offsetToXY(reference.startOffset),
            end = offsetToXY(reference.endOffset),
            lineHeight = lineHeight,
            pointer = pointer,
        )

    private suspend fun handleRequest(request: HoverRequest) {
        val target = readAction { request.resolvePopupTarget() }

        if (target == null) {
            withContext(Dispatchers.EDT) {
                if (activeHoverKey == request.popupKey()) {
                    activeHoverKey = null
                    if (!isPointerInsidePopup()) {
                        hidePopup()
                    }
                }
            }

            return
        }

        val indexService = project.service<DesignTokenIndexService>()
        val indexCached = readAction { indexService.isIndexCached(target.sourceFile) }

        if (!indexCached) {
            withContext(Dispatchers.EDT) {
                if (activeHoverKey == target.key) {
                    showLoadingPopup(
                        editor = request.editor,
                        anchor = request.anchor,
                        key = target.key,
                        tokenName = target.tokenName,
                    )
                }
            }
        }

        val popupData = readAction { target.resolvePopupData(indexService) }

        withContext(Dispatchers.EDT) {
            if (activeHoverKey != target.key) {
                return@withContext
            }

            if (popupData == null) {
                activeHoverKey = null
                if (!isPointerInsidePopup()) {
                    hidePopup()
                }
            } else {
                showResolvedPopup(request.editor, request.anchor, popupData)
            }
        }
    }

    private fun HoverRequest.resolvePopupTarget(): PopupTarget? =
        reference
            ?.takeIf { !project.isDisposed && !editor.isDisposed }
            ?.takeIf { modificationStamp == editor.document.modificationStamp }
            ?.let { validReference ->
                FileDocumentManager
                    .getInstance()
                    .getFile(editor.document)
                    ?.takeIf { file -> file.extension?.lowercase() in SUPPORTED_EXTENSIONS }
                    ?.path
                    ?.let { path -> runCatching { Path.of(path) }.getOrNull() }
                    ?.let { sourceFile ->
                        PopupTarget(
                            key =
                                PopupKey(
                                    editor = editor,
                                    tokenName = validReference.name,
                                    offset = validReference.startOffset,
                                    modificationStamp = modificationStamp,
                                ),
                            sourceFile = sourceFile,
                            tokenName = validReference.name,
                        )
                    }
            }

    private fun PopupTarget.resolvePopupData(indexService: DesignTokenIndexService): PopupData? =
        indexService
            .resolveToken(sourceFile, tokenName)
            .takeIf { groups -> groups.isNotEmpty() }
            ?.let { groups ->
                PopupData(
                    key = key,
                    model = DesignTokenHoverPopupModel.create(tokenName, groups),
                )
            }

    private fun showLoadingPopup(
        editor: Editor,
        anchor: Point,
        key: PopupKey,
        tokenName: String,
    ) {
        showPopup(editor, anchor, key) { panel ->
            panel.showLoading(tokenName)
        }
    }

    private fun showResolvedPopup(
        editor: Editor,
        anchor: Point,
        data: PopupData,
    ) {
        if (popupKey == data.key && popup?.isVisible == true) {
            popupContent?.showModel(data.model)
            popup?.moveToFitScreen()

            return
        }

        showPopup(editor, anchor, data.key) { panel ->
            panel.showModel(data.model)
        }
    }

    private fun showPopup(
        editor: Editor,
        anchor: Point,
        key: PopupKey,
        initializePanel: (DesignTokenHoverPopupPanel) -> Unit,
    ) {
        if (popupKey == key && popup?.isVisible == true) {
            return
        }

        hidePopup()
        nativeHoverPopupSuppression.suppress(editor)

        val popupWidth = calculatePopupWidth(editor)
        var popupReference: JBPopup? = null
        val panel =
            DesignTokenHoverPopupPanel(
                popupWidth = popupWidth,
                onNavigate = ::navigateToDefinition,
                onReportBug = { BrowserUtil.browse(REPORT_BUG_URL) },
                onPreferredSizeChanged = { size ->
                    popupReference
                        ?.takeIf { currentPopup -> currentPopup.isVisible && !currentPopup.isDisposed }
                        ?.let { currentPopup ->
                            currentPopup.setSize(size)
                            currentPopup.moveToFitScreen()
                        }
                },
            ).also(initializePanel)
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
                .setResizable(false)
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
                        activeHoverKey = null
                        nativeHoverPopupSuppression.restore()
                    }
                }
            },
        )
        popup = createdPopup
        popupContent = panel
        popupKey = key
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

    private fun navigateToDefinition(target: DesignTokenNavigationTarget) {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target.sourceFile) ?: return

        OpenFileDescriptor(
            project,
            file,
            (target.line - 1).coerceAtLeast(0),
            0,
        ).navigate(true)
        activeHoverKey = null
        hidePopup()
    }

    private fun isPointerInsidePopup(): Boolean {
        val content = popupContent?.takeIf { component -> component.isShowing }
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

    private fun HoverRequest.popupKey(): PopupKey? =
        reference?.let { validReference ->
            PopupKey(
                editor = editor,
                tokenName = validReference.name,
                offset = validReference.startOffset,
                modificationStamp = modificationStamp,
            )
        }

    private data class HoverRequest(
        val editor: Editor,
        val reference: DesignTokenReferenceAtOffset?,
        val anchor: Point,
        val modificationStamp: Long,
    )

    private data class PopupKey(
        val editor: Editor,
        val tokenName: String,
        val offset: Int,
        val modificationStamp: Long,
    )

    private data class PopupTarget(
        val key: PopupKey,
        val sourceFile: Path,
        val tokenName: String,
    )

    private data class PopupData(
        val key: PopupKey,
        val model: DesignTokenHoverPopupModel,
    )

    private companion object {
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
