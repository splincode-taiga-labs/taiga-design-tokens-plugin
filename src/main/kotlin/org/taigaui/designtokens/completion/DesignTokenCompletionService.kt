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
import java.nio.file.Path

@Service(Service.Level.PROJECT)
internal class DesignTokenCompletionService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val lock = Any()
    private val snapshots = mutableMapOf<Path, List<String>>()
    private val pendingWarmups = mutableSetOf<Path>()
    private val pendingCallbacks = mutableMapOf<Path, MutableList<PendingCallback>>()

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
        val snapshot = synchronized(lock) { snapshots[normalizedSourceFile] }

        if (indexService.isIndexCached(normalizedSourceFile)) {
            return indexService
                .completionTokenNames(normalizedSourceFile)
                .also { names -> storeSnapshot(normalizedSourceFile, names) }
        }

        scheduleWarmup(
            sourceFile = normalizedSourceFile,
            callback = PendingCallback(onUpdated, notifyWhenUnchanged),
        )

        return snapshot.takeIf { allowStaleSnapshot }
    }

    private fun scheduleWarmup(
        sourceFile: Path,
        callback: PendingCallback,
    ) {
        val shouldStart =
            synchronized(lock) {
                pendingCallbacks
                    .getOrPut(sourceFile, ::mutableListOf)
                    .add(callback)
                pendingWarmups.add(sourceFile)
            }

        if (!shouldStart) {
            return
        }

        coroutineScope.launch(CoroutineName("Taiga UI design token completion warmup")) {
            var completed = false

            try {
                val names = project.service<DesignTokenIndexService>().completionTokenNames(sourceFile)

                publishWarmup(sourceFile, names)
                completed = true
            } finally {
                if (!completed) {
                    synchronized(lock) {
                        pendingWarmups.remove(sourceFile)
                        pendingCallbacks.remove(sourceFile)
                    }
                }
            }
        }
    }

    private suspend fun publishWarmup(
        sourceFile: Path,
        names: List<String>,
    ) {
        val callbacks =
            synchronized(lock) {
                val previous = snapshots.put(sourceFile, names)
                val namesChanged = previous != names
                val pending = pendingCallbacks.remove(sourceFile).orEmpty()

                pendingWarmups.remove(sourceFile)
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
        sourceFile: Path,
        names: List<String>,
    ) {
        synchronized(lock) {
            snapshots[sourceFile] = names
        }
    }

    private data class PendingCallback(
        val callback: () -> Unit,
        val notifyWhenUnchanged: Boolean,
    )
}
