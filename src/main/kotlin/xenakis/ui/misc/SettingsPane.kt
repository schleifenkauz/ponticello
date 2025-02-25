package xenakis.ui.misc

import hextant.context.Context
import hextant.fx.children
import hextant.fx.hbox
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import xenakis.impl.toDecimal
import xenakis.model.Settings
import xenakis.model.score.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.ui.controls.Knob
import xenakis.ui.impl.centerChildren
import xenakis.ui.impl.styleClass
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
                    +hbox(
                        Knob(
                            "Latency (sclang)", KnobControl(settings.scLangLatency),
                            NumericalControlSpec(0.1, 0.01, 1.0, 0.01.toDecimal()), context = context
                        )
                    )
                    +hbox(
                        Knob(
                            "Latency (scsynth)", KnobControl(settings.serverLatency),
                            NumericalControlSpec(0.1, 0.01, 1.0, 0.01.toDecimal()), context = context
                        )
                    )
                    +hbox(
                        Knob(
                            "Forced Garbage collection period", KnobControl(settings.garbageCollectionPeriod),
                            NumericalControlSpec(60.0, 10.0, 240.0, 10.0.toDecimal()), context = context
                        )
                    )
                }
            }
        }
    }
}