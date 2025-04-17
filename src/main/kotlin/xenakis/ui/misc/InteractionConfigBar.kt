package xenakis.ui.misc

import fxutils.KeyEventHandlerBody
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.actions.registerActions
import fxutils.infiniteSpace
import fxutils.prompt.SimpleSearchableListView
import fxutils.styleClass
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.model.project.UIState
import xenakis.model.project.UIState.SnapOption

class InteractionConfigBar(private val settings: UIState) : HBox() {
    private val snapToggle = toggleSnap.withContext(settings)
    private val gridToggle = toggleGrid.withContext(settings)

    private val optionList = object : SimpleSearchableListView<SnapOption>(SnapOption.entries, "Select snap option") {
        override fun createCell(option: SnapOption): Region = HBox(
            5.0,
            Label(option.name.lowercase()),
            infiniteSpace(),
            Label(shortcutFor(option))
        )
    }
    private val optionButton = optionList.selectorButton(settings.snapOption)

    private fun shortcutFor(option: SnapOption) = when (option) {
        SnapOption.Seconds -> "Alt+S"
        SnapOption.Bars -> "Alt+B"
        SnapOption.Beats -> "Alt+N"
        SnapOption.Ticks -> "Alt+T"
    }

    init {
        styleClass("toolbar-part")
        optionButton.disableProperty().bind(settings.snapEnabled.not().asObservableValue())
        children.addAll(
            snapToggle.makeButton("large-icon-button"),
            gridToggle.makeButton("large-icon-button"),
            optionButton
        )
    }

    fun addGridRelatedShortcuts(body: KeyEventHandlerBody<Unit>) {
        for (option in SnapOption.entries) {
            val shortcut = shortcutFor(option)
            body.on(shortcut){
                settings.snapOption.now = option
            }
        }
        body.registerActions(listOf(snapToggle, gridToggle))
    }


    companion object {
        private val toggleSnap = action<UIState>("Toggle snapping") {
            shortcut("Q")
            icon { settings ->
                settings.snapEnabled.map { enabled ->
                    if (enabled) MaterialDesignM.MAGNET_ON
                    else MaterialDesignM.MAGNET
                }
            }
            toggles(UIState::snapEnabled)
        }

        private val toggleGrid = action("Toggle time grid") {
            shortcut("T")
            icon(Material2AL.LINEAR_SCALE)
            toggles(UIState::displayTimeGrid)
        }

    }
}