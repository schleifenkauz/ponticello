package xenakis.ui.misc

import fxutils.*
import hextant.context.Context
import javafx.scene.control.Label
import reaktive.value.ReactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.toDecimal
import xenakis.model.Settings
import xenakis.model.score.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.ui.controls.Knob
import xenakis.ui.registry.ParameterDefsPane
import xenakis.ui.registry.ToolPane

class SettingsPane(settings: Settings, private val context: Context) : ToolPane() {
    init {
        styleClass("settings-pane")
        setup("Settings", vbox {
            children {
                +ParameterDefsPane(settings.defaultParametersDefs, context, title = "Default parameter control specs")
                +Label("Playback options").styleClass("heading")
                item("Latency: ") {
                    +Knob(
                        "Latency (sclang) in ms", KnobControl(settings.scLangLatency),
                        NumericalControlSpec(0.1, 0.01, 1.0, 0.01.toDecimal()), context = context
                    )
                    +Knob(
                        "Latency (scsynth) in ms", KnobControl(settings.serverLatency),
                        NumericalControlSpec(0.1, 0.01, 1.0, 0.01.toDecimal()), context = context
                    )
                }
                knobItem(
                    "Garbage collection interval: ", settings.garbageCollectionPeriod,
                    NumericalControlSpec(60.0, 10.0, 240.0, 10.0.toDecimal())
                )
                knobItem(
                    "Knob sensitivity: ", settings.knobSensitivity,
                    NumericalControlSpec(default = 3.0, 1.0, 10.0, 0.1.toDecimal(), Warp.Linear)
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
            +Knob(name, KnobControl(variable), spec, context)
        }
    }
}