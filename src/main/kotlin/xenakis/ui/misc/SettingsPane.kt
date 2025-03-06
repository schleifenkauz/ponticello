package xenakis.ui.misc

import fxutils.centerChildren
import fxutils.children
import fxutils.hbox
import fxutils.styleClass
import hextant.context.Context
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import xenakis.impl.toDecimal
import xenakis.model.Settings
import xenakis.model.score.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.ui.controls.Knob
import xenakis.ui.registry.ParameterDefsPane

class SettingsPane(settings: Settings, context: Context) : VBox(5.0) {
    init {
        styleClass("settings-pane")
        children {
            +Label("Parameter control specs:").styleClass("heading")
            +ParameterDefsPane(context, settings.defaultParametersDefs)
            +Label("Playback options").styleClass("heading")
            +hbox {
                spacing = 10.0
                centerChildren()
                children {
                    +Label("Latency: ")
                    +Knob(
                        "Latency (sclang) in ms", KnobControl(settings.scLangLatency),
                        NumericalControlSpec(0.1, 0.01, 1.0, 0.01.toDecimal()), context = context
                    )
                    +Knob(
                        "Latency (scsynth) in ms", KnobControl(settings.serverLatency),
                        NumericalControlSpec(0.1, 0.01, 1.0, 0.01.toDecimal()), context = context
                    )
                }
            }
            +hbox {
                spacing = 10.0
                centerChildren()
                children {
                    +Label("Garbage collection interval (seconds): ")
                    Knob(
                        "Forced Garbage collection every ", KnobControl(settings.garbageCollectionPeriod),
                        NumericalControlSpec(60.0, 10.0, 240.0, 10.0.toDecimal()), context = context
                    )
                }
            }
        }
    }
}