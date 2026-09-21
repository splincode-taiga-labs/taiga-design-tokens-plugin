package org.taigaui.designtokens.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.taigaui.designtokens.documentation.DesignTokenHoverPopupModel
import org.taigaui.designtokens.project.DesignTokenIndexService
import org.taigaui.designtokens.settings.TaigaDesignTokensSettings
import java.awt.Dimension
import java.awt.Point
import java.nio.file.Path
import javax.swing.JLayeredPane

@Service(Service.Level.PROJECT)
internal class DesignTokenCompletionPreviewController(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private var activeLookup: Lookup? = null
    private var activeListener: LookupListener? = null
    private var previewHint: LightweightHint? = null
    private var previewPanel: DesignTokenCompletionPreviewPanel? = null
    private var previewJob: Job? = null
    private var previewKey: PreviewKey? = null

    init {
        project.messageBus
            .connect()
            .subscribe(
                LookupManagerListener.TOPIC,
                object : LookupManagerListener {
                    override fun activeLookupChanged(
                        oldLookup: Lookup?,
                        newLookup: Lookup?,
                    ) {
                        attach(newLookup)
                    }
                },
            )
    }

    fun ensureAttached() {
        coroutineScope.launch(Dispatchers.EDT) {
            attach(LookupManager.getInstance(project).activeLookup)
        }
    }

    private fun attach(lookup: Lookup?) {
        if (activeLookup === lookup) {
            lookup?.let(::requestPreview)
            return
        }

        detach()

        if (lookup?.isCompletion != true) {
            return
        }

        val listener =
            object : LookupListener {
                override fun lookupShown(event: LookupEvent) {
                    requestPreview(lookup)
                }

                override fun currentItemChanged(event: LookupEvent) {
                    requestPreview(lookup)
                }

                override fun uiRefreshed() {
                    requestPreview(lookup)
                }

                override fun itemSelected(event: LookupEvent) {
                    hidePreview()
                }

                override fun lookupCanceled(event: LookupEvent) {
                    hidePreview()
                }
            }

        activeLookup = lookup
        activeListener = listener
        lookup.addLookupListener(listener)
        requestPreview(lookup)
    }

    private fun detach() {
        val lookup = activeLookup
        val listener = activeListener

        if (lookup != null && listener != null) {
            lookup.removeLookupListener(listener)
        }

        activeLookup = null
        activeListener = null
        previewJob?.cancel()
        previewJob = null
        previewKey = null
        hidePreview()
    }

    private fun requestPreview(lookup: Lookup) {
        if (!service<TaigaDesignTokensSettings>().showCompletionPreview) {
            clearPreviewRequest()
            return
        }

        val request = lookup.previewRequest()

        if (request == null) {
            clearPreviewRequest()
            return
        }

        val key = PreviewKey(lookup, request.tokenName, request.sourceFile)

        if (previewKey == key && previewJob?.isActive == true) {
            return
        }

        previewKey = key
        previewJob?.cancel()

        if (previewHint?.isVisible != true) {
            showLoading(lookup, request.tokenName)
        }

        val indexService = project.service<DesignTokenIndexService>()

        previewJob =
            coroutineScope.launch(CoroutineName("Taiga UI design token completion preview")) {
                val groups =
                    withContext(Dispatchers.Default) {
                        indexService.resolveToken(request.sourceFile, request.tokenName)
                    }
                val model =
                    groups
                        .takeIf { values -> values.isNotEmpty() }
                        ?.let { values ->
                            DesignTokenHoverPopupModel.create(
                                tokenName = request.tokenName,
                                groups = values,
                            )
                        }

                withContext(Dispatchers.EDT) {
                    if (
                        previewKey == key &&
                        activeLookup === lookup &&
                        lookup.currentTokenName() == request.tokenName
                    ) {
                        if (model == null) {
                            hidePreview()
                        } else {
                            showModel(lookup, model)
                        }
                    }
                }
            }
    }

    private fun clearPreviewRequest() {
        previewKey = null
        previewJob?.cancel()
        previewJob = null
        hidePreview()
    }

    private fun showLoading(
        lookup: Lookup,
        tokenName: String,
    ) {
        val panel = previewPanel ?: DesignTokenCompletionPreviewPanel().also { previewPanel = it }

        panel.showLoading(tokenName)
        showOrMoveHint(lookup, panel.preferredSize)
    }

    private fun showModel(
        lookup: Lookup,
        model: DesignTokenHoverPopupModel,
    ) {
        val panel = previewPanel ?: DesignTokenCompletionPreviewPanel().also { previewPanel = it }

        panel.showModel(model)
        showOrMoveHint(lookup, panel.preferredSize)
    }

    private fun showOrMoveHint(
        lookup: Lookup,
        previewSize: Dimension,
    ) {
        val editor = lookup.topLevelEditor
        val layeredPane = editor.contentComponent.rootPane?.layeredPane ?: return
        val location = previewLocation(lookup, previewSize, layeredPane)
        val hint =
            previewHint
                ?: LightweightHint(requireNotNull(previewPanel))
                    .apply {
                        setForceLightweightPopup(true)
                        setCancelOnClickOutside(false)
                        setBelongsToGlobalPopupStack(false)
                        setCancelOnOtherWindowOpen(false)
                    }.also { previewHint = it }

        if (hint.isVisible) {
            hint.pack()
            hint.updateLocation(location.x, location.y)
        } else {
            hint.show(
                layeredPane,
                location.x,
                location.y,
                editor.contentComponent,
                HintHint().setRequestFocus(false),
            )
        }
    }

    private fun hidePreview() {
        previewHint?.takeIf { hint -> hint.isVisible }?.hide()
    }
}

private fun Lookup.previewRequest(): CompletionPreviewRequest? =
    currentTokenName()?.let { tokenName ->
        sourceFilePath()?.let { sourceFile ->
            DesignTokenCompletionContextFinder
                .find(
                    text = topLevelEditor.document.immutableCharSequence,
                    offset = topLevelEditor.caretModel.offset,
                )?.takeIf { context -> context.prefix.startsWith(TAIGA_TOKEN_ROOT) }
                ?.let {
                    CompletionPreviewRequest(
                        tokenName = tokenName,
                        sourceFile = sourceFile,
                    )
                }
        }
    }

private fun Lookup.sourceFilePath(): Path? =
    psiFile
        ?.virtualFile
        ?.path
        ?.let(::pathOrNull)
        ?: FileDocumentManager
            .getInstance()
            .getFile(topLevelEditor.document)
            ?.path
            ?.let(::pathOrNull)

private fun Lookup.currentTokenName(): String? =
    currentItem
        ?.lookupString
        ?.let(::normalizeDesignTokenLookupString)

internal fun normalizeDesignTokenLookupString(value: String): String? =
    when {
        value.startsWith(TAIGA_TOKEN_ROOT) -> value
        value.startsWith(TAIGA_TOKEN_BARE_ROOT) -> "--$value"
        else -> null
    }

private fun previewLocation(
    lookup: Lookup,
    previewSize: Dimension,
    layeredPane: JLayeredPane,
): Point {
    val lookupBounds = lookup.bounds
    val gap = JBUI.scale(PREVIEW_GAP)
    val rightX = lookupBounds.x + lookupBounds.width + gap
    val leftX = lookupBounds.x - previewSize.width - gap
    val fitsRight = rightX + previewSize.width <= layeredPane.width
    val x = if (fitsRight) rightX else leftX.coerceAtLeast(0)
    val maxY = (layeredPane.height - previewSize.height).coerceAtLeast(0)

    return Point(x, lookupBounds.y.coerceIn(0, maxY))
}

private data class CompletionPreviewRequest(
    val tokenName: String,
    val sourceFile: Path,
)

private data class PreviewKey(
    val lookup: Lookup,
    val tokenName: String,
    val sourceFile: Path,
)

private const val TAIGA_TOKEN_ROOT = "--tui-"
private const val TAIGA_TOKEN_BARE_ROOT = "tui-"
private const val PREVIEW_GAP = 8
