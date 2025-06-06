package ponticello.ui.misc

import fxutils.*
import hextant.context.Context
import javafx.scene.control.Label
import ponticello.impl.Decimal
import ponticello.impl.toDecimal
import ponticello.model.Settings
import ponticello.sc.NumericalControlSpec
import ponticello.ui.controls.Knob
import ponticello.ui.registry.ParameterDefsPane
import ponticello.ui.registry.ToolPane
import reaktive.value.ReactiveVariable

class SettingsPane(settings: Settings, private val context: Context) : ToolPane() {
    init {
        styleClass("settings-pane")
        setup(vbox {
            children {
                +ParameterDefsPane(settings.defaultParametersDefs, context, title = "Default parameter control specs")
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
        })
    }

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
}