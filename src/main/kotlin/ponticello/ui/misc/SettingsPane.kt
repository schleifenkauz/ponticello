package ponticello.ui.misc

import fxutils.children
import fxutils.controls.CheckBox
import fxutils.hbox
import fxutils.styleClass
import fxutils.vbox
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.impl.toDecimal
import ponticello.model.GlobalSettings
import ponticello.model.project.PonticelloProject
import ponticello.sc.NumericalControlSpec
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.misc.PlaybackSettingsPrompt.Companion.latencyKnobsPane
import ponticello.ui.registry.ParameterDefsPane

class SettingsPane(private val settings: GlobalSettings) : ToolPane() {
    override val type: Type
        get() = SettingsPane
    override val content: Parent by lazy {
        vbox {
            children {
                +ParameterDefsPane(settings.defaultParametersDefs, title = "Default parameter control specs").apply {
                    prefHeight = 500.0
                }
                +Label("Playback options").styleClass("heading")
                +BorderPane(Label("Latency").styleClass("sub-heading"))
                +latencyKnobsPane(settings.scLangLatency, settings.serverLatency, settings.extraLatency)

                +CheckBox(settings.logScCode, "Log SuperCollider code: ")
                knobItem(
                    "Knob sensitivity: ", settings.knobSensitivity,
                    NumericalControlSpec(default = 3.0, 1.0, 10.0, 0.1.toDecimal())
                )
                +Label("Garbage collection").styleClass("sub-heading")
                hbox {
                    +CheckBox(settings.periodicGarbageCollection, "Periodic GC: ")
                    knobItem(
                        "Garbage collection interval: ", settings.garbageCollectionPeriod,
                        NumericalControlSpec(60.0, 10.0, 240.0, 10.0.toDecimal())
                    )
                }
            }
        }
    }

    init {
        styleClass("settings-pane")
    }

    override fun defaultState(): ToolPaneState = ToolPaneState.window

    companion object : Type(1, "Settings") {
        override val icon: Ikon
            get() = MaterialDesignC.COG

        override val defaultSide: Side
            get() = Side.TOP

        override val shortcut: String
            get() = "Ctrl+Alt+S"

        override fun createToolPane(project: PonticelloProject): ToolPane =
            SettingsPane(project.context[GlobalSettings])
    }
}

