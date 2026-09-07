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
import org.taigaui.designtokens.project.DesignTokenIndexService
import org.taigaui.designtokens.project.TokenContextKey
import java.nio.file.Path

@Service(Service.Level.PROJECT)
internal class DesignTokenCompletionService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val lock = Any()
    private val snapshots = mutableMapOf<TokenContextKey, List<String>>()
    private val pendingWarmups = mutableSetOf<TokenContextKey>()
    private val pendingCallbacks = mutableMapOf<TokenContextKey, MutableList<PendingCallback>>()

    fun namesFor(
        sourceFile: Path,
        onUpdated: () -> Unit,
    ): List<String>? =
        namesFor(
            sourceFile = sourceFile,
            onUpdated = onUpdated,
            allowStaleSnapshot = true,
            notifyWhenUnchanged = false,
        )

    fun namesForInspection(
        sourceFile: Path,
        onUpdated: () -> Unit,
    ): List<String>? =
        namesFor(
            sourceFile = sourceFile,
            onUpdated = onUpdated,
            allowStaleSnapshot = false,
            notifyWhenUnchanged = true,
        )

    private fun namesFor(
        sourceFile: Path,
        onUpdated: () -> Unit,
        allowStaleSnapshot: Boolean,
        notifyWhenUnchanged: Boolean,
    ): List<String>? {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val indexService = project.service<DesignTokenIndexService>()
        val contextKey = indexService.contextKey(normalizedSourceFile)
        val snapshot = synchronized(lock) { snapshots[contextKey] }

        if (indexService.isIndexCached(normalizedSourceFile)) {
            return indexService
                .completionTokenNames(normalizedSourceFile)
                .also { names -> storeSnapshot(contextKey, names) }
        }

        scheduleWarmup(
            contextKey = contextKey,
            sourceFile = normalizedSourceFile,
            callback = PendingCallback(onUpdated, notifyWhenUnchanged),
        )

        return snapshot.takeIf { allowStaleSnapshot }
    }

    private fun scheduleWarmup(
        contextKey: TokenContextKey,
        sourceFile: Path,
        callback: PendingCallback,
    ) {
        val shouldStart =
            synchronized(lock) {
                pendingCallbacks
                    .getOrPut(contextKey, ::mutableListOf)
                    .add(callback)
                pendingWarmups.add(contextKey)
            }

        if (!shouldStart) {
            return
        }

        coroutineScope.launch(CoroutineName("Taiga UI design token completion warmup")) {
            var completed = false

            try {
                val names = project.service<DesignTokenIndexService>().completionTokenNames(sourceFile)

                publishWarmup(contextKey, names)
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
        names: List<String>,
    ) {
        val callbacks =
            synchronized(lock) {
                val previous = snapshots.put(contextKey, names)
                val namesChanged = previous != names
                val pending = pendingCallbacks.remove(contextKey).orEmpty()

                pendingWarmups.remove(contextKey)
                pending
                    .filter { callback -> namesChanged || callback.notifyWhenUnchanged }
                    .map(PendingCallback::callback)
                    .takeIf { names.isNotEmpty() }
                    .orEmpty()
            }

        if (callbacks.isNotEmpty() && !project.isDisposed) {
            withContext(Dispatchers.EDT) {
                callbacks.forEach { callback -> callback() }
            }
        }
    }

    private fun storeSnapshot(
        contextKey: TokenContextKey,
        names: List<String>,
    ) {
        synchronized(lock) {
            snapshots[contextKey] = names
        }
    }

    private data class PendingCallback(
        val callback: () -> Unit,
        val notifyWhenUnchanged: Boolean,
    )
}
