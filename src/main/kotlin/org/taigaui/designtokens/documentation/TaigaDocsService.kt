package org.taigaui.designtokens.documentation

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

@Service(Service.Level.PROJECT)
internal class TaigaDocsService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val versionDetector = TaigaUiVersionDetector()
    private val indexStore = TaigaDocsIndexStore(coroutineScope)

    suspend fun snapshotFor(sourceFile: Path): TaigaDocsSnapshot? {
        val context = detectContext(sourceFile)
        val source = context?.let { TaigaDocsSources.forMajor(it.majorVersion) }
        val index = source?.let { indexStore.indexFor(it) }

        return if (context != null && index != null) TaigaDocsSnapshot(context, index) else null
    }

    suspend fun refresh(sourceFile: Path): TaigaDocsSnapshot? {
        val context = detectContext(sourceFile)
        val source = context?.let { TaigaDocsSources.forMajor(it.majorVersion) }
        val index = source?.let { indexStore.refresh(it) }

        return if (context != null && index != null) TaigaDocsSnapshot(context, index) else null
    }

    fun warmUp(sourceFile: Path) {
        if (project.isDisposed) {
            return
        }

        coroutineScope.launch(Dispatchers.IO + CoroutineName("Taiga UI documentation warmup")) {
            snapshotFor(sourceFile)
        }
    }

    fun cachedIndexFor(majorVersion: Int): TaigaDocsIndex? =
        TaigaDocsSources.forMajor(majorVersion)?.let(indexStore::cached)

    fun invalidate(
        majorVersion: Int,
        removeDiskCache: Boolean = false,
    ) {
        TaigaDocsSources.forMajor(majorVersion)?.let { source ->
            indexStore.invalidate(source, removeDiskCache)
        }
    }

    internal fun clearMemory() {
        indexStore.clearMemory()
    }

    private suspend fun detectContext(sourceFile: Path): TaigaUiProjectContext? =
        withContext(Dispatchers.IO) {
            versionDetector.detect(sourceFile.toAbsolutePath().normalize())
        }
}
