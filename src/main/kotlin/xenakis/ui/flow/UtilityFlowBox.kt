package xenakis.ui.flow

import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.HBox
import xenakis.model.flow.UtilityFlow

class UtilityFlowBox(flow: UtilityFlow) : FlowBox<UtilityFlow>(flow) {
    override fun getContent(flow: UtilityFlow): Node {
        val slider = Slider(-61.0, +6.0, 0.0) styleClass "volume-fader"
        return HBox(slider)
    }

    override fun getTitle(flow: UtilityFlow): Node = Label("Utility")
}