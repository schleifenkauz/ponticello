package xenakis.ui

import hextant.context.Context
import hextant.fx.children
import hextant.fx.hbox
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import xenakis.impl.Knob
import xenakis.model.KnobControl
import xenakis.model.Settings
import xenakis.sc.NumericalControlSpec

class SettingsPane(settings: Settings, context: Context) : VBox(5.0) {
    init {
        styleClass("settings-pane")
        children {
            +Label("Parameter control specs:").styleClass("heading")
            +ParameterDefsPane(context, settings.defaultParametersDefs)
            +Label("Playback options").styleClass("heading")
            +hbox {
                spacing = 10.0
                centerChildrenVertically()
                children {
                    +Label("Latency: ")
                    +hbox(
                        Knob(
                            "Latency (sclang)", KnobControl(settings.scLangLatency),
                            NumericalControlSpec(0.1, 0.01, 1.0, 0.01), context = context
                        )
                    )
                    +hbox(
                        Knob(
                            "Latency (scsynth)", KnobControl(settings.serverLatency),
                            NumericalControlSpec(0.1, 0.01, 1.0, 0.01), context = context
                        )
                    )
                }
            }
        }
    }
}