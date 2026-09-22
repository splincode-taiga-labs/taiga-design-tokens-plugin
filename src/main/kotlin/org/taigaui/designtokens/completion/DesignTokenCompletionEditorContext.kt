package org.taigaui.designtokens.completion

import com.intellij.openapi.editor.Editor
import org.taigaui.designtokens.documentation.designTokenStyleTextContext

internal fun Editor.designTokenCompletionContextAt(offset: Int): DesignTokenCompletionContext? =
    designTokenStyleTextContext(offset)?.let { context ->
        DesignTokenCompletionContextFinder.find(
            text = context.text,
            offset = context.offset,
        )
    }
