package org.taigaui.designtokens.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

internal class TaigaDesignTokensConfigurable : BoundConfigurable("Taiga UI") {
    override fun createPanel(): DialogPanel {
        val settings = service<TaigaDesignTokensSettings>()

        return panel {
            group("Design tokens") {
                row {
                    checkBox("Show source details in completion preview")
                        .bindSelected(
                            { settings.showCompletionSourceDetails },
                            { value -> settings.showCompletionSourceDetails = value },
                        )
                }
                row {
                    checkBox("Show source files and selector contexts in token hover")
                        .bindSelected(
                            { settings.showHoverSourceDetails },
                            { value -> settings.showHoverSourceDetails = value },
                        )
                }
            }
        }
    }
}
