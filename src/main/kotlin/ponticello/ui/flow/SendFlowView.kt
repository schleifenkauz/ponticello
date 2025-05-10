package ponticello.ui.flow

import bundles.createBundle
import fxutils.centerChildren
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import ponticello.impl.toDecimal
import ponticello.model.flow.SendFlow
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.sc.editor.BusSelector
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.controls.Knob

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