package xenakis.ui.flow

import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.control.Slider
import javafx.scene.layout.HBox
import reaktive.value.ReactiveString
import reaktive.value.reactiveValue
import xenakis.model.flow.UtilityFlow

class UtilityFlowBox(flow: UtilityFlow) : FlowBox<UtilityFlow>(flow) {
    override fun getContent(flow: UtilityFlow): Node {
        val slider = Slider(-61.0, +6.0, 0.0) styleClass "volume-fader"
        return HBox(slider)
    }

    override fun getTitle(flow: UtilityFlow): ReactiveString = reactiveValue("Utility")
}