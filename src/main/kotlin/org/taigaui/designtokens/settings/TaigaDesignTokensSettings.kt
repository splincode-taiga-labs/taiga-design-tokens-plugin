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
    var showCompletionPreview: Boolean
        get() = state.showCompletionPreview
        set(value) {
            state.showCompletionPreview = value
        }

    var showHoverPopup: Boolean
        get() = state.showHoverPopup
        set(value) {
            state.showHoverPopup = value
        }

    internal class SettingsState : BaseState() {
        var showCompletionPreview by property(true)
        var showHoverPopup by property(true)
    }
}
