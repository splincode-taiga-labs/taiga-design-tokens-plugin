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
    private val pendingCallbacks = mutableMapOf<Path, () -> Unit>()

    fun namesFor(
        sourceFile: Path,
        onUpdated: () -> Unit,
    ): List<String>? {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val indexService = project.service<DesignTokenIndexService>()
        val snapshot = synchronized(lock) { snapshots[normalizedSourceFile] }

        if (indexService.isIndexCached(normalizedSourceFile)) {
            return indexService
                .completionTokenNames(normalizedSourceFile)
                .also { names -> storeSnapshot(normalizedSourceFile, names) }
        }

        scheduleWarmup(normalizedSourceFile, onUpdated)

        return snapshot
    }

    private fun scheduleWarmup(
        sourceFile: Path,
        onUpdated: () -> Unit,
    ) {
        val shouldStart =
            synchronized(lock) {
                pendingCallbacks[sourceFile] = onUpdated
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
        val update =
            synchronized(lock) {
                val previous = snapshots.put(sourceFile, names)
                val callback = pendingCallbacks.remove(sourceFile)

                pendingWarmups.remove(sourceFile)
                CompletionUpdate(
                    callback = callback,
                    shouldNotify = names.isNotEmpty() && previous != names,
                )
            }

        if (update.shouldNotify && !project.isDisposed) {
            withContext(Dispatchers.EDT) {
                update.callback?.invoke()
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

    private data class CompletionUpdate(
        val callback: (() -> Unit)?,
        val shouldNotify: Boolean,
    )
}
