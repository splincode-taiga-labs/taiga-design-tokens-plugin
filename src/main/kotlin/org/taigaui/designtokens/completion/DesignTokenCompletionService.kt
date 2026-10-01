package org.taigaui.designtokens.completion

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.taigaui.designtokens.cache.CoalescingRefreshCallbacks
import org.taigaui.designtokens.cache.RefreshCallback
import org.taigaui.designtokens.project.DesignTokenCatalogEntry
import org.taigaui.designtokens.project.DesignTokenIndexService
import org.taigaui.designtokens.project.TokenContextKey
import java.nio.file.Path

@Service(Service.Level.PROJECT)
internal class DesignTokenCompletionService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val lock = Any()
    private val snapshots = mutableMapOf<TokenContextKey, List<DesignTokenCatalogEntry>>()
    private val pendingWarmups = mutableSetOf<TokenContextKey>()
    private val pendingCallbacks = mutableMapOf<TokenContextKey, CoalescingRefreshCallbacks>()

    fun namesFor(
        sourceFile: Path,
        onUpdated: RefreshCallback<*>,
    ): List<String>? = entriesFor(sourceFile, onUpdated)?.map(DesignTokenCatalogEntry::name)

    fun namesForInspection(
        sourceFile: Path,
        onUpdated: RefreshCallback<*>,
    ): List<String>? = entriesForInspection(sourceFile, onUpdated)?.map(DesignTokenCatalogEntry::name)

    fun entriesFor(
        sourceFile: Path,
        onUpdated: RefreshCallback<*>,
    ): List<DesignTokenCatalogEntry>? =
        entriesFor(
            sourceFile = sourceFile,
            onUpdated = onUpdated,
            allowStaleSnapshot = true,
        )

    fun entriesForInspection(
        sourceFile: Path,
        onUpdated: RefreshCallback<*>,
    ): List<DesignTokenCatalogEntry>? =
        entriesFor(
            sourceFile = sourceFile,
            onUpdated = onUpdated,
            allowStaleSnapshot = false,
        )

    private fun entriesFor(
        sourceFile: Path,
        onUpdated: RefreshCallback<*>,
        allowStaleSnapshot: Boolean,
    ): List<DesignTokenCatalogEntry>? {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val indexService = project.service<DesignTokenIndexService>()
        val contextKey = indexService.contextKey(normalizedSourceFile)
        val snapshot = synchronized(lock) { snapshots[contextKey] }

        if (indexService.isIndexCached(normalizedSourceFile)) {
            return indexService
                .completionTokenCatalog(normalizedSourceFile)
                .also { entries -> storeSnapshot(contextKey, entries) }
        }

        scheduleWarmup(
            contextKey = contextKey,
            sourceFile = normalizedSourceFile,
            callback = onUpdated,
        )

        return snapshot.takeIf { allowStaleSnapshot }
    }

    private fun scheduleWarmup(
        contextKey: TokenContextKey,
        sourceFile: Path,
        callback: RefreshCallback<*>,
    ) {
        val shouldStart =
            synchronized(lock) {
                pendingCallbacks
                    .getOrPut(contextKey, ::CoalescingRefreshCallbacks)
                    .add(callback)
                pendingWarmups.add(contextKey)
            }

        if (!shouldStart) {
            return
        }

        coroutineScope.launch(CoroutineName("Taiga UI design token completion warmup")) {
            var completed = false

            try {
                val entries = project.service<DesignTokenIndexService>().completionTokenCatalog(sourceFile)

                publishWarmup(contextKey, entries)
                completed = true
            } finally {
                if (!completed) {
                    synchronized(lock) {
                        pendingWarmups.remove(contextKey)
                        pendingCallbacks.remove(contextKey)
                    }
                }
            }
        }
    }

    private suspend fun publishWarmup(
        contextKey: TokenContextKey,
        entries: List<DesignTokenCatalogEntry>,
    ) {
        val callbacks =
            synchronized(lock) {
                val previous = snapshots.put(contextKey, entries)
                val entriesChanged = previous != entries
                val pending = pendingCallbacks.remove(contextKey)

                pendingWarmups.remove(contextKey)

                if (entries.isEmpty()) {
                    emptyList()
                } else {
                    pending?.take(entriesChanged).orEmpty()
                }
            }

        if (callbacks.isNotEmpty() && !project.isDisposed) {
            withContext(Dispatchers.EDT) {
                callbacks.forEach { callback -> callback.invokeIfActive() }
            }
        }
    }

    private fun storeSnapshot(
        contextKey: TokenContextKey,
        entries: List<DesignTokenCatalogEntry>,
    ) {
        synchronized(lock) {
            snapshots[contextKey] = entries
        }
    }
}
