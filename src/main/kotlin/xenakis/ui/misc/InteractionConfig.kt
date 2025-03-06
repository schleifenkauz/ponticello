package xenakis.ui.misc

import fxutils.KeyEventHandlerBody
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.styleClass
import javafx.collections.FXCollections
import javafx.scene.control.ComboBox
import javafx.scene.layout.HBox
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asProperty
import reaktive.value.now
import xenakis.model.InteractionSettings
import xenakis.model.InteractionSettings.SnapOption
import xenakis.model.XenakisProject

class InteractionConfig(settings: InteractionSettings) : HBox() {
    private val snapToggle = toggleSnap.withContext(settings).makeButton("large-icon-button")
    private val gridToggle = toggleGrid.withContext(settings).makeButton("large-icon-button")

    private val snapOption = ComboBox(FXCollections.observableList(SnapOption.entries))

    init {
        styleClass("toolbar-part")
        snapOption.disableProperty().bind(settings.snapEnabled.not().asObservableValue())
        snapOption.valueProperty().bindBidirectional(settings.snapOption.asProperty())
        children.addAll(snapToggle, gridToggle, snapOption)
    }

    companion object {
        private val toggleSnap = action<InteractionSettings>("Toggle snapping") {
            shortcut("Q")
            icon { settings ->
                settings.snapEnabled.map { enabled ->
                    if (enabled) MaterialDesignM.MAGNET_ON //TODO why doesn't it update the icon?
                    else MaterialDesignM.MAGNET
                }
            }
            toggles { s -> s.snapEnabled }
        }

        private val toggleGrid = action<InteractionSettings>("Toggle time grid") {
            shortcut("T")
            icon(Material2AL.LINEAR_SCALE)
            toggles { s -> s.displayTimeGrid }
        }

        fun addGridRelatedShortcuts(body: KeyEventHandlerBody<Unit>, project: XenakisProject) = with(body) {
            on("Alt+S") { project.settings.snapOption.now = SnapOption.Seconds }
            on("Alt+B") { project.settings.snapOption.now = SnapOption.Bars }
            on("Alt+N") { project.settings.snapOption.now = SnapOption.Beats }
            on("Alt+T") { project.settings.snapOption.now = SnapOption.Ticks }
        }
    }
}