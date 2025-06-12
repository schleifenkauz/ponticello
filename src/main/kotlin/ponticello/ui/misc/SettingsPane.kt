package ponticello.ui.misc

import fxutils.*
import javafx.scene.Parent
import javafx.scene.control.Label
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.impl.Decimal
import ponticello.impl.toDecimal
import ponticello.model.Settings
import ponticello.model.project.PonticelloProject
import ponticello.sc.NumericalControlSpec
import ponticello.ui.controls.Knob
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.registry.ParameterDefsPane
import reaktive.value.ReactiveVariable

class SettingsPane(private val settings: Settings) : ToolPane() {
    override val type: Type
        get() = SettingsPane
    override val content: Parent by lazy {
        vbox {
            children {
                +ParameterDefsPane(settings.defaultParametersDefs, title = "Default parameter control specs")
                +Label("Playback options").styleClass("heading")
                item("Latency: ") {
                    +Knob(
                        "Latency (sclang) in ms", settings.scLangLatency,
                        NumericalControlSpec(0.1, 0.01, 1.0, 0.01.toDecimal())
                    )
                    +Knob(
                        "Latency (scsynth) in ms", settings.serverLatency,
                        NumericalControlSpec(0.1, 0.01, 1.0, 0.01.toDecimal())
                    )
                    +Knob(
                        "Extra latency (ms)", settings.extraLatency,
                        NumericalControlSpec(0.05, 0.0, 1.0, 0.01.toDecimal())
                    )
                }
                knobItem(
                    "Garbage collection interval: ", settings.garbageCollectionPeriod,
                    NumericalControlSpec(60.0, 10.0, 240.0, 10.0.toDecimal())
                )
                knobItem(
                    "Knob sensitivity: ", settings.knobSensitivity,
                    NumericalControlSpec(default = 3.0, 1.0, 10.0, 0.1.toDecimal())
                )
            }
        }
    }

    init {
        styleClass("settings-pane")
    }

    override fun defaultState(): ToolPaneState = ToolPaneState.window

    private fun ChildrenAdder.item(name: String, children: ChildrenAdder.() -> Unit) {
        +hbox {
            spacing = 10.0
            centerChildren()
            children {
                +Label(name)
                children()
            }
        }
    }

    private fun ChildrenAdder.knobItem(name: String, variable: ReactiveVariable<Decimal>, spec: NumericalControlSpec) {
        item(name) {
            +Knob(name, variable, spec)
        }
    }

    companion object : Type(1, "Settings") {
        override val icon: Ikon
            get() = MaterialDesignC.COG

        override val defaultSide: Side
            get() = Side.TOP

        override fun createToolPane(project: PonticelloProject): ToolPane = SettingsPane(project.context[Settings])
    }
}