package xenakis.ui.misc

import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.layout.HBox
import xenakis.model.InteractionSettings
import xenakis.model.InteractionSettings.SnapOption
import xenakis.ui.actions.Actions
import xenakis.ui.actions.makeToggleButton
import xenakis.ui.impl.styleClass

class InteractionConfig(settings: InteractionSettings) : HBox() {
    private val snapToggle = Actions.interactionConfig.getAction("Toggle snapping")
        .withContext(settings)
        .makeToggleButton(settings.snapEnabled)
    private val gridToggle = Actions.interactionConfig.getAction("Toggle time grid")
        .withContext(settings)
        .makeToggleButton(settings.displayTimeGrid)

    private val snapOption = ComboBox(FXCollections.observableList(SnapOption.entries))

    init {
        styleClass("toolbar-part")
        snapOption.disableProperty().bind(snapToggle.selectedProperty().not())
        children.addAll(snapToggle, gridToggle, snapOption)
    }
}