package xenakis.ui

import hextant.context.Context
import hextant.context.createControl
import hextant.core.view.ListEditorControl
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import xenakis.model.Settings

class SettingsPane(settings: Settings, context: Context) : VBox(5.0) {
    init {
        children.add(Label("Parameter control specs:"))
        children.add(context.createControl(settings.defaultParametersDefs) {
            set(ListEditorControl.ORIENTATION, ListEditorControl.Orientation.Vertical)
            set(ListEditorControl.EMPTY_DISPLAY) { null }
        })
        children.add(button("Add default parameter spec") { settings.defaultParametersDefs.addLast() })
    }
}