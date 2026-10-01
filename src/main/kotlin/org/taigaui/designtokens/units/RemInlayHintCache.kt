package org.taigaui.designtokens.units

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.util.WeakHashMap

@Service(Service.Level.PROJECT)
internal class RemInlayHintCache(
    private val project: Project,
) {
    private val lock = Any()
    private val entries = WeakHashMap<PsiFile, MutableMap<RemInlayHintKind, Entry>>()

    fun hints(
        file: PsiFile,
        kind: RemInlayHintKind,
        compute: () -> List<RemInlayHint>,
    ): List<RemInlayHint> {
        while (true) {
            val stampBefore = modificationStamp(file)
            val cached = cached(file, kind, stampBefore)

            if (cached != null) {
                return cached
            }

            val hints = compute()
            val stampAfter = modificationStamp(file)

            if (stampBefore == stampAfter) {
                return publish(file, kind, stampAfter, hints)
            }
        }
    }

    private fun modificationStamp(file: PsiFile): Long =
        PsiDocumentManager
            .getInstance(project)
            .getDocument(file)
            ?.modificationStamp
            ?: file.viewProvider.modificationStamp

    private fun cached(
        file: PsiFile,
        kind: RemInlayHintKind,
        modificationStamp: Long,
    ): List<RemInlayHint>? =
        synchronized(lock) {
            entries[file]
                ?.get(kind)
                ?.takeIf { entry -> entry.modificationStamp == modificationStamp }
                ?.hints
        }

    private fun publish(
        file: PsiFile,
        kind: RemInlayHintKind,
        modificationStamp: Long,
        hints: List<RemInlayHint>,
    ): List<RemInlayHint> =
        synchronized(lock) {
            val byKind = entries.getOrPut(file, ::linkedMapOf)

            byKind[kind]
                ?.takeIf { entry -> entry.modificationStamp == modificationStamp }
                ?.hints
                ?: hints.also { currentHints ->
                    byKind[kind] =
                        Entry(
                            modificationStamp = modificationStamp,
                            hints = currentHints,
                        )
                }
        }

    private data class Entry(
        val modificationStamp: Long,
        val hints: List<RemInlayHint>,
    )
}

internal enum class RemInlayHintKind {
    STYLESHEET,
    ANGULAR_TEMPLATE,
    ANGULAR_HOST,
}
