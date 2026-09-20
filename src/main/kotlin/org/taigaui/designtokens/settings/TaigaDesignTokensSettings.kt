package org.taigaui.designtokens.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "TaigaDesignTokensSettings",
    storages = [Storage("taiga-ui-design-tokens.xml")],
)
internal class TaigaDesignTokensSettings :
    SimplePersistentStateComponent<TaigaDesignTokensSettings.SettingsState>(SettingsState()) {
    var showCompletionSourceDetails: Boolean
        get() = state.showCompletionSourceDetails
        set(value) {
            state.showCompletionSourceDetails = value
        }

    var showHoverSourceDetails: Boolean
        get() = state.showHoverSourceDetails
        set(value) {
            state.showHoverSourceDetails = value
        }

    internal class SettingsState : BaseState() {
        var showCompletionSourceDetails by property(false)
        var showHoverSourceDetails by property(false)
    }
}
