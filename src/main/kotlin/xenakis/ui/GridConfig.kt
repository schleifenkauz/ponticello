package xenakis.ui

import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.layout.HBox

class GridConfig : HBox() {
    val snapToggle = Icon.Snap.toggleButton(description = "Snap cursor")
    val gridToggle = Icon.TimeGrid.toggleButton(description = "Display time grid")
    val snapOption = ComboBox(FXCollections.observableList(SnapOption.values().asList()))

    init {
        styleClass("toolbar-part")
        snapOption.value = SnapOption.Seconds
        snapOption.disableProperty().bind(snapToggle.selectedProperty().not())
        children.addAll(snapToggle, gridToggle, snapOption)
    }

    enum class SnapOption {
        Seconds, Bars, Beats, Ticks;
    }
}