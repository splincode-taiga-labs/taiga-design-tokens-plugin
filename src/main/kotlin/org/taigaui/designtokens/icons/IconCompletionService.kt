package org.taigaui.designtokens.icons

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.taigaui.designtokens.diagnostics.PerformanceDiagnostics
import org.taigaui.designtokens.diagnostics.PerformanceMetric
import java.nio.file.Path

@Service(Service.Level.PROJECT)
internal class IconCompletionService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val loader = IconCatalogLoader()
    private val lock = Any()
    private val snapshots = mutableMapOf<Path, IconCatalog>()
    private val pendingCallbacks = mutableMapOf<Path, MutableList<() -> Unit>>()

    fun namesFor(
        sourceFile: Path,
        onUpdated: () -> Unit,
    ): List<String>? =
        loader
            .resolveScopeRoot(sourceFile)
            ?.toAbsolutePath()
            ?.normalize()
            ?.let { scopeRoot -> namesForScope(scopeRoot, onUpdated) }
            ?: emptyList()

    fun svgSourceFor(
        sourceFile: Path,
        iconName: String,
    ): IconSvgSource? {
        val scopeRoot =
            loader
                .resolveScopeRoot(sourceFile)
                ?.toAbsolutePath()
                ?.normalize()
                ?: return null
        val snapshot = synchronized(lock) { snapshots[scopeRoot] }

        return snapshot?.svgSource(iconName)
    }

    internal fun loadNow(sourceFile: Path): List<String> =
        loader
            .resolveScopeRoot(sourceFile)
            ?.toAbsolutePath()
            ?.normalize()
            ?.let(::loadScopeNow)
            .orEmpty()

    private fun loadScopeNow(scopeRoot: Path): List<String> {
        val existing = synchronized(lock) { snapshots[scopeRoot] }
        val catalog =
            existing
                ?: loadCatalog(scopeRoot).also { loaded ->
                    synchronized(lock) {
                        snapshots[scopeRoot] = loaded
                    }
                }

        return catalog.names
    }

    private fun namesForScope(
        scopeRoot: Path,
        onUpdated: () -> Unit,
    ): List<String>? {
        val snapshot = synchronized(lock) { snapshots[scopeRoot] }

        if (snapshot == null) {
            scheduleWarmup(scopeRoot, onUpdated)
        }

        return snapshot?.names
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
            val catalog = loadCatalog(scopeRoot)
            val callbacks =
                synchronized(lock) {
                    snapshots[scopeRoot] = catalog
                    pendingCallbacks.remove(scopeRoot).orEmpty()
                }

            if (callbacks.isNotEmpty() && !project.isDisposed) {
                withContext(Dispatchers.EDT) {
                    callbacks.forEach { current -> current() }
                }
            }
        }
    }

    private fun loadCatalog(scopeRoot: Path): IconCatalog =
        PerformanceDiagnostics.measure(PerformanceMetric.ICON_CATALOG_LOAD) {
            loader.loadCatalog(scopeRoot)
        }
}
