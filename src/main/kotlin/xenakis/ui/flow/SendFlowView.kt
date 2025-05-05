package xenakis.ui.flow

import bundles.createBundle
import fxutils.centerChildren
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import xenakis.impl.toDecimal
import xenakis.model.flow.SendFlow
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.editor.BusSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.controls.Knob

class SendFlowView(flow: SendFlow) : HBox() {
    init {
        val spec = NumericalControlSpec(100.0, 0.0, 100.0, 1.toDecimal(), 0.02, Warp.Linear, Color.GREEN)
        val knob = Knob("Amount", (flow.amountPercent), spec, color = Color.gray(0.3))
        val targetBusSelector = BusSelector()
        targetBusSelector.syncWith(flow.targetRef)
        targetBusSelector.initialize(flow.context)
        val selectorControl = ObjectSelectorControl(targetBusSelector, createBundle())
        children.addAll(knob, selectorControl)
        centerChildren()
        spacing = 10.0
    }
}