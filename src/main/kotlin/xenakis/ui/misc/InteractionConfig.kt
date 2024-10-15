package xenakis.ui.misc

import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.layout.HBox
import reaktive.value.fx.asProperty
import xenakis.model.InteractionSettings
import xenakis.model.InteractionSettings.SnapOption
import xenakis.ui.Icon
import xenakis.ui.impl.styleClass

class InteractionConfig(settings: InteractionSettings) : HBox() {
    private val snapToggle = Icon.Snap.toggleButton(description = "Snap cursor")
    private val gridToggle = Icon.TimeGrid.toggleButton(description = "Display time grid")
    private val snapOption = ComboBox(FXCollections.observableList(SnapOption.entries))

    init {
        styleClass("toolbar-part")
        snapToggle.selectedProperty().bindBidirectional(settings.snapEnabled.asProperty())
        snapOption.valueProperty().bindBidirectional(settings.snapOption.asProperty())
        gridToggle.selectedProperty().bindBidirectional(settings.displayTimeGrid.asProperty())

        snapOption.disableProperty().bind(snapToggle.selectedProperty().not())
        children.addAll(snapToggle, gridToggle, snapOption)
    }
}