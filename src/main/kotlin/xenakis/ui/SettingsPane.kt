package xenakis.ui

import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import xenakis.model.Settings

class SettingsPane(settings: Settings, context: Context) : VBox(5.0) {
    init {
        children.add(Label("Parameter control specs:"))
        children.add(ParameterDefsPane(context, settings.defaultParametersDefs))
    }
}