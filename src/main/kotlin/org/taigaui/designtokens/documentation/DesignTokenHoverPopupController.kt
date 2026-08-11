package org.taigaui.designtokens.documentation

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.LookupManagerListener
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
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.taigaui.designtokens.project.DesignTokenIndexService
import java.awt.MouseInfo
import java.awt.Point
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
internal class DesignTokenHoverPopupController(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
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
    private var latestHoverRequest: HoverRequest? = null
    private var pendingHideJob: Job? = null

    init {
        coroutineScope.launch(CoroutineName("Taiga UI design token hover popup")) {
            requests.collectLatest(::handleRequest)
        }

        project.messageBus
            .connect()
            .subscribe(
                LookupManagerListener.TOPIC,
                object : LookupManagerListener {
                    override fun activeLookupChanged(
                        oldLookup: Lookup?,
                        newLookup: Lookup?,
                    ) {
                        if (newLookup?.isCompletion == true) {
                            coroutineScope.launch(Dispatchers.EDT) {
                                cancelScheduledHide()
                                activeHoverKey = null
                                latestHoverRequest = null
                                hidePopup()
                            }
                        }
                    }
                },
            )
    }

    fun mouseMoved(event: EditorMouseEvent) {
        val editor = event.editor

        if (project.hasActiveCompletionLookup()) {
            cancelScheduledHide()
            activeHoverKey = null
            latestHoverRequest = null
            hidePopup()
            return
        }

        if (editor.project == project && !editor.isDisposed) {
            val anchor = Point(event.mouseEvent.point)
            val reference = event.findReferenceUnderPointer(anchor)

            if (reference == null) {
                latestHoverRequest = null

                if (popup?.isVisible == true) {
                    scheduleHide()
                } else {
                    activeHoverKey = null
                    nativeHoverPopupSuppression.restore()
                }

                return
            }

            cancelScheduledHide()
            nativeHoverPopupSuppression.suppress(editor)

            val request =
                HoverRequest(
                    editor = editor,
                    reference = reference,
                    anchor = anchor,
                    modificationStamp = editor.document.modificationStamp,
                )
            val requestKey = request.popupKey()

            latestHoverRequest = request

            if (requestKey != activeHoverKey) {
                activeHoverKey = requestKey
                requests.tryEmit(request)
            }
        }
    }

    private suspend fun handleRequest(initialRequest: HoverRequest) {
        delay(HOVER_SHOW_DELAY)

        val request =
            withContext(Dispatchers.EDT) {
                if (project.hasActiveCompletionLookup()) {
                    activeHoverKey = null
                    latestHoverRequest = null
                    hidePopup()
                    return@withContext null
                }

                if (!canShowDesignTokenPopup(popup)) {
                    activeHoverKey = null
                    latestHoverRequest = null
                    nativeHoverPopupSuppression.restore()
                    return@withContext null
                }

                latestHoverRequest
                    ?.takeIf { latest ->
                        initialRequest.popupKey() == activeHoverKey &&
                            latest.popupKey() == activeHoverKey
                    }
            } ?: return
        val target = readAction { request.resolvePopupTarget(project) }

        if (target == null) {
            withContext(Dispatchers.EDT) {
                if (activeHoverKey == request.popupKey()) {
                    activeHoverKey = null
                    latestHoverRequest = null
                    if (!isPointerInsidePopup()) {
                        hidePopup()
                    }
                }
            }

            return
        }

        val indexService = project.service<DesignTokenIndexService>()
        val indexCached =
            withContext(Dispatchers.Default) {
                indexService.isIndexCached(target.sourceFile)
            }

        if (!indexCached) {
            withContext(Dispatchers.EDT) {
                showLoadingPopup(
                    editor = request.editor,
                    anchor = request.anchor,
                    key = target.key,
                    tokenName = target.tokenName,
                )
            }
        }

        val popupData =
            withContext(Dispatchers.Default) {
                target.resolvePopupData(indexService)
            }

        showPopupDataIfCurrent(request, target, popupData)
    }

    private suspend fun showPopupDataIfCurrent(
        request: HoverRequest,
        target: PopupTarget,
        popupData: PopupData,
    ) {
        withContext(Dispatchers.EDT) {
            val requestStillValid =
                request.modificationStamp == request.editor.document.modificationStamp

            if (activeHoverKey == target.key && requestStillValid) {
                if (project.hasActiveCompletionLookup()) {
                    activeHoverKey = null
                    latestHoverRequest = null
                    hidePopup()
                } else if (!canShowDesignTokenPopup(popup)) {
                    activeHoverKey = null
                    latestHoverRequest = null
                    nativeHoverPopupSuppression.restore()
                } else if (popupKey == popupData.key && popup?.isVisible == true) {
                    popupContent?.showModel(popupData.model)
                } else {
                    showPopup(request.editor, request.anchor, popupData.key) { panel ->
                        panel.showModel(popupData.model)
                    }
                }
            }
        }
    }

    private fun showLoadingPopup(
        editor: Editor,
        anchor: Point,
        key: PopupKey,
        tokenName: String,
    ) {
        if (
            !project.hasActiveCompletionLookup() &&
            activeHoverKey == key &&
            canShowDesignTokenPopup(popup)
        ) {
            showPopup(editor, anchor, key) { panel ->
                panel.showLoading(tokenName)
            }
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

        if (project.hasActiveCompletionLookup() || !canShowDesignTokenPopup(popup)) {
            return
        }

        hidePopup()
        nativeHoverPopupSuppression.suppress(editor)

        val popupWidth = editor.calculateDesignTokenPopupWidth()
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
                            currentPopup.setLocation(popupLocationAtAnchor(editor, anchor))
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
                .createPopup()

        popupReference = createdPopup
        createdPopup.addListener(
            object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    if (popup === createdPopup) {
                        cancelScheduledHide()
                        popup = null
                        popupContent = null
                        popupKey = null
                        activeHoverKey = null
                        latestHoverRequest = null
                        nativeHoverPopupSuppression.restore()
                    }
                }
            },
        )
        popup = createdPopup
        popupContent = panel
        popupKey = key
        createdPopup.showInScreenCoordinates(
            editor.contentComponent,
            popupLocationAtAnchor(editor, anchor),
        )
        createdPopup.setLocation(popupLocationAtAnchor(editor, anchor))
        createdPopup.moveToFitScreen()
    }

    private fun scheduleHide() {
        if (popup?.isVisible != true && activeHoverKey == null) {
            return
        }

        pendingHideJob?.cancel()
        pendingHideJob =
            coroutineScope.launch(CoroutineName("Taiga UI design token hover popup hide")) {
                delay(HIDE_GRACE_PERIOD)

                withContext(Dispatchers.EDT) {
                    pendingHideJob = null

                    if (!isPointerInsidePopup()) {
                        activeHoverKey = null
                        latestHoverRequest = null
                        hidePopup()
                    }
                }
            }
    }

    private fun cancelScheduledHide() {
        pendingHideJob?.cancel()
        pendingHideJob = null
    }

    private fun navigateToDefinition(target: DesignTokenNavigationTarget) {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target.sourceFile) ?: return

        OpenFileDescriptor(
            project,
            file,
            (target.line - 1).coerceAtLeast(0),
            0,
        ).navigate(true)
        cancelScheduledHide()
        activeHoverKey = null
        latestHoverRequest = null
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
}

private fun Project.hasActiveCompletionLookup(): Boolean =
    LookupManager.getInstance(this).activeLookup?.isCompletion == true

private fun canShowDesignTokenPopup(currentPopup: JBPopup?): Boolean =
    currentPopup?.isVisible == true || !JBPopupFactory.getInstance().isPopupActive

private fun Editor.calculateDesignTokenPopupWidth(): Int {
    val preferredWidth = JBUI.scale(PREFERRED_POPUP_WIDTH)
    val minimumWidth = JBUI.scale(MIN_POPUP_WIDTH)
    val availableWidth =
        contentComponent.graphicsConfiguration
            ?.bounds
            ?.width
            ?.let { screenWidth -> (screenWidth * MAX_SCREEN_WIDTH_RATIO).toInt() }
            ?: preferredWidth

    return preferredWidth
        .coerceAtMost(availableWidth)
        .coerceAtLeast(minimumWidth.coerceAtMost(availableWidth))
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

private fun HoverRequest.resolvePopupTarget(project: Project): PopupTarget? =
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

private fun PopupTarget.resolvePopupData(indexService: DesignTokenIndexService): PopupData {
    val groups = indexService.resolveToken(sourceFile, tokenName)
    val model =
        if (groups.isEmpty()) {
            DesignTokenHoverPopupModel(
                tokenName = tokenName,
                description = "Nothing found",
                sections = emptyList(),
            )
        } else {
            DesignTokenHoverPopupModel.create(tokenName, groups)
        }

    return PopupData(
        key = key,
        model = model,
    )
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

private fun popupLocationAtAnchor(
    editor: Editor,
    anchor: Point,
): Point {
    val screenAnchor = Point(anchor)

    SwingUtilities.convertPointToScreen(screenAnchor, editor.contentComponent)

    return Point(
        screenAnchor.x - JBUI.scale(CURSOR_X_INSET),
        screenAnchor.y,
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

private val HOVER_SHOW_DELAY = 500.milliseconds
private val HIDE_GRACE_PERIOD = 250.milliseconds
private val SUPPORTED_EXTENSIONS = setOf("css", "less", "scss")
private const val PREFERRED_POPUP_WIDTH = 560
private const val MIN_POPUP_WIDTH = 460
private const val MAX_SCREEN_WIDTH_RATIO = 0.72
private const val CURSOR_X_INSET = 16
private const val REPORT_BUG_URL =
    "https://github.com/taiga-family-labs/taiga-design-tokens-plugin/issues/new?labels=bug"
