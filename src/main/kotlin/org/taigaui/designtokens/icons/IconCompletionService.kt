package org.taigaui.designtokens.icons

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

@Service(Service.Level.PROJECT)
internal class IconCompletionService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val loader = IconCatalogLoader()
    private val lock = Any()
    private val snapshots = mutableMapOf<Path, List<String>>()
    private val pendingCallbacks = mutableMapOf<Path, MutableList<() -> Unit>>()

    fun namesFor(
        sourceFile: Path,
        onUpdated: () -> Unit,
    ): List<String>? {
        val scopeRoot = loader.resolveScopeRoot(sourceFile) ?: return emptyList()
        val normalizedScope = scopeRoot.toAbsolutePath().normalize()
        val snapshot = synchronized(lock) { snapshots[normalizedScope] }

        if (snapshot != null) {
            return snapshot
        }

        scheduleWarmup(normalizedScope, onUpdated)

        return null
    }

    internal fun loadNow(sourceFile: Path): List<String> {
        val scopeRoot = loader.resolveScopeRoot(sourceFile) ?: return emptyList()
        val normalizedScope = scopeRoot.toAbsolutePath().normalize()
        val names = loader.load(normalizedScope)

        synchronized(lock) {
            snapshots[normalizedScope] = names
        }

        return names
    }

    private fun scheduleWarmup(
        scopeRoot: Path,
        callback: () -> Unit,
    ) {
        val shouldStart =
            synchronized(lock) {
                val callbacks = pendingCallbacks.getOrPut(scopeRoot, ::mutableListOf)

                callbacks.add(callback)
                callbacks.size == 1
            }

        if (!shouldStart) {
            return
        }

        coroutineScope.launch(Dispatchers.IO + CoroutineName("Taiga UI icon completion warmup")) {
            val names = loader.load(scopeRoot)
            val callbacks =
                synchronized(lock) {
                    snapshots[scopeRoot] = names
                    pendingCallbacks.remove(scopeRoot).orEmpty()
                }

            if (callbacks.isNotEmpty() && !project.isDisposed) {
                withContext(Dispatchers.EDT) {
                    callbacks.forEach { current -> current() }
                }
            }
        }
    }
}
