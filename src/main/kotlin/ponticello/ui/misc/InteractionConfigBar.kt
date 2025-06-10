package ponticello.ui.misc

import fxutils.*
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.actions.registerActions
import fxutils.controls.CheckBox
import fxutils.prompt.DetailPane
import fxutils.prompt.SimpleSearchableListView
import fxutils.undo.UndoManager
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.score.TimeUnit
import ponticello.ui.impl.DEFAULT_SCENE_FILL
import ponticello.ui.impl.sceneFill
import reaktive.value.binding.map
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.now

class InteractionConfigBar(private val settings: UIState) : HBox() {
    private val snapToggle = toggleSnap.withContext(settings)

    private val optionList = object : SimpleSearchableListView<TimeUnit>(TimeUnit.entries, "Select snap option") {
        override fun createCell(option: TimeUnit): Region = HBox(
            5.0,
            Label(option.name.lowercase()),
            infiniteSpace(),
            Label(shortcutFor(option))
        )
    }
    private val optionButton = optionList.selectorButton(settings.snapOption)

    private fun shortcutFor(option: TimeUnit) = when (option) {
        TimeUnit.Seconds -> "Alt+S"
        TimeUnit.Bars -> "Alt+B"
        TimeUnit.Beats -> "Alt+N"
        TimeUnit.Ticks -> "Alt+T"
    }

    init {
        styleClass("toolbar-part")
        optionButton.disableProperty().bind(settings.snapEnabled.not().asObservableValue())
        children.addAll(
            snapToggle.makeButton("large-icon-button"),
            optionButton,
            showExtraSettings.withContext(settings).makeButton("medium-icon-button")
        )
    }

    fun addGridRelatedShortcuts(body: KeyEventHandlerBody<Unit>) {
        for (option in TimeUnit.entries) {
            val shortcut = shortcutFor(option)
            body.on(shortcut) {
                settings.snapOption.now = option
            }
        }
        body.registerActions(listOf(snapToggle))
    }


    companion object {
        private fun createDetailsPane(config: UIState): DetailPane = DetailPane(labelWidth = 160.0).apply {
            addItem(
                "Ask for group names",
                CheckBox(config.askForGroupNames)
                    .setupUndo(config.context[UndoManager], "Ask for group names")
            )
            addItem(
                "Ask for clone names",
                CheckBox(config.askForCloneNames)
                    .setupUndo(config.context[UndoManager], "Ask for clone names")
            )
            addItem(
                "Inline display mode",
                SimpleSearchableListView(InlineControlsDisplay.entries, "Select controls display mode")
                    .selectorButton(
                        config.controlsDisplay,
                        undoManager = config.context[UndoManager],
                        actionDescription = "Select inline display mode"
                    ).setFixedWidth(140.0)
            )
        }

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

        private val showExtraSettings = action("Extra settings") {
            icon(MaterialDesignD.DOTS_VERTICAL)
            executes { settings, ev ->
                val detailsPane = createDetailsPane(settings)
                val popup = SubWindow(detailsPane, "Interaction configuration", SubWindow.Type.Popup)
                    .sceneFill(DEFAULT_SCENE_FILL.opacity(0.5))
                popup.show(ev.popupAnchor())
            }
        }
    }
}