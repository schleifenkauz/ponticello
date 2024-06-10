package xenakis.ui

import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.layout.HBox
import reaktive.value.fx.asProperty
import xenakis.model.InteractionSettings
import xenakis.model.InteractionSettings.SnapOption

class InteractionConfig(settings: InteractionSettings) : HBox() {
    val snapToggle = Icon.Snap.toggleButton(description = "Snap cursor")
    val gridToggle = Icon.TimeGrid.toggleButton(description = "Display time grid")
    val snapOption = ComboBox(FXCollections.observableList(SnapOption.values().asList()))

    init {
        styleClass("toolbar-part")
        snapToggle.selectedProperty().bindBidirectional(settings.snapEnabled.asProperty())
        snapOption.valueProperty().bindBidirectional(settings.snapOption.asProperty())
        gridToggle.selectedProperty().bindBidirectional(settings.displayTimeGrid.asProperty())

        snapOption.disableProperty().bind(snapToggle.selectedProperty().not())
        children.addAll(snapToggle, gridToggle, snapOption)
    }
}