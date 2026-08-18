package org.taigaui.designtokens.icons

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
import com.intellij.util.SVGLoader
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.Image
import java.awt.Point
import java.net.URI
import java.nio.file.Path
import javax.swing.JLayeredPane

@Service(Service.Level.PROJECT)
internal class IconCompletionPreviewController(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val imageCache = mutableMapOf<URI, Image>()
    private var activeLookup: Lookup? = null
    private var activeListener: LookupListener? = null
    private var previewHint: LightweightHint? = null
    private var previewPanel: IconCompletionPreviewPanel? = null
    private var previewJob: Job? = null
    private var previewKey: IconPreviewKey? = null

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

        if (lookup?.isCompletion != true || !lookup.supportsIconPreview()) {
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
        clearPreviewRequest()
    }

    private fun requestPreview(lookup: Lookup) {
        val request = lookup.iconPreviewRequest()

        if (request == null) {
            clearPreviewRequest()
            return
        }

        val source =
            project
                .service<IconCompletionService>()
                .svgSourceFor(request.sourceFile, request.iconName)

        if (source == null) {
            clearPreviewRequest()
            return
        }

        val key = IconPreviewKey(lookup, request.iconName, source.uri)

        if (previewKey == key && previewJob?.isActive == true) {
            return
        }

        previewKey = key
        previewJob?.cancel()
        showLoading(lookup, request.iconName)

        previewJob =
            coroutineScope.launch(Dispatchers.IO + CoroutineName("Taiga UI icon completion preview")) {
                val image = loadImage(source)

                withContext(Dispatchers.EDT) {
                    if (
                        previewKey == key &&
                        activeLookup === lookup &&
                        lookup.currentIconName() == request.iconName
                    ) {
                        if (image == null) {
                            hidePreview()
                        } else {
                            showIcon(lookup, request.iconName, image)
                        }
                    }
                }
            }
    }

    private fun loadImage(source: IconSvgSource): Image? {
        synchronized(imageCache) {
            imageCache[source.uri]?.let { return it }
        }

        val image = runCatching { SVGLoader.load(source.uri.toURL(), 1f) }.getOrNull() ?: return null

        synchronized(imageCache) {
            imageCache[source.uri] = image
        }

        return image
    }

    private fun clearPreviewRequest() {
        previewKey = null
        previewJob?.cancel()
        previewJob = null
        hidePreview()
    }

    private fun showLoading(
        lookup: Lookup,
        iconName: String,
    ) {
        val panel = previewPanel ?: IconCompletionPreviewPanel().also { previewPanel = it }

        panel.showLoading(iconName)
        showOrMoveHint(lookup, panel.preferredSize)
    }

    private fun showIcon(
        lookup: Lookup,
        iconName: String,
        image: Image,
    ) {
        val panel = previewPanel ?: IconCompletionPreviewPanel().also { previewPanel = it }

        panel.showIcon(iconName, image)
        showOrMoveHint(lookup, panel.preferredSize)
    }

    private fun showOrMoveHint(
        lookup: Lookup,
        previewSize: Dimension,
    ) {
        val editor = lookup.topLevelEditor
        val layeredPane = editor.contentComponent.rootPane?.layeredPane ?: return
        val location = iconPreviewLocation(lookup, previewSize, layeredPane)
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

private fun Lookup.iconPreviewRequest(): IconPreviewRequest? =
    currentIconName()?.let { iconName ->
        sourceFilePath()?.let { sourceFile ->
            IconCompletionContextFinder
                .find(
                    text = topLevelEditor.document.immutableCharSequence,
                    offset = topLevelEditor.caretModel.offset,
                )?.let {
                    IconPreviewRequest(
                        iconName = iconName,
                        sourceFile = sourceFile,
                    )
                }
        }
    }

private fun Lookup.sourceFilePath(): Path? =
    psiFile
        ?.virtualFile
        ?.path
        ?.let(::iconPathOrNull)
        ?: FileDocumentManager
            .getInstance()
            .getFile(topLevelEditor.document)
            ?.path
            ?.let(::iconPathOrNull)

private fun Lookup.currentIconName(): String? =
    currentItem
        ?.lookupString
        ?.takeIf { value -> value.startsWith(ICON_PREFIX) }

private fun Lookup.supportsIconPreview(): Boolean =
    psiFile?.virtualFile?.extension?.lowercase() in ICON_SUPPORTED_EXTENSIONS ||
        FileDocumentManager
            .getInstance()
            .getFile(topLevelEditor.document)
            ?.extension
            ?.lowercase() in ICON_SUPPORTED_EXTENSIONS

private fun iconPreviewLocation(
    lookup: Lookup,
    previewSize: Dimension,
    layeredPane: JLayeredPane,
): Point {
    val lookupBounds = lookup.bounds
    val gap = JBUI.scale(PREVIEW_GAP)
    val leftX = lookupBounds.x - previewSize.width - gap
    val rightX = lookupBounds.x + lookupBounds.width + gap
    val x = if (leftX >= 0) leftX else rightX.coerceAtMost((layeredPane.width - previewSize.width).coerceAtLeast(0))
    val maxY = (layeredPane.height - previewSize.height).coerceAtLeast(0)

    return Point(x, lookupBounds.y.coerceIn(0, maxY))
}

private fun iconPathOrNull(value: String): Path? = runCatching { Path.of(value) }.getOrNull()

private data class IconPreviewRequest(
    val iconName: String,
    val sourceFile: Path,
)

private data class IconPreviewKey(
    val lookup: Lookup,
    val iconName: String,
    val sourceUri: URI,
)

private const val PREVIEW_GAP = 8
