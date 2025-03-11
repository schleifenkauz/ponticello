package xenakis.ui.flow

import fxutils.styleClass
import javafx.scene.Node
import javafx.scene.control.Slider
import xenakis.model.flow.UtilityFlow

class UtilityFlowBox(flow: UtilityFlow) : FlowBox<UtilityFlow>(flow) {
    override fun getContent(): Node {
        val slider = Slider(-60.0, +6.0, 0.0) styleClass "volume-fader"
        return slider
    }
}