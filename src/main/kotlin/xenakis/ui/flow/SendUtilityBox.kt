package xenakis.ui.flow

import bundles.createBundle
import fxutils.centerChildren
import javafx.scene.Node
import javafx.scene.layout.HBox
import reaktive.value.reactiveValue
import xenakis.impl.toDecimal
import xenakis.model.flow.SendFlow
import xenakis.model.score.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.editor.BusSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.controls.Knob

class SendUtilityBox(flow: SendFlow) : FlowBox<SendFlow>(flow) {
    override fun getContent(): Node {
        val ctrl = KnobControl(flow.amountPercent)
        val spec = NumericalControlSpec(100.0, 0.0, 100.0, 1.toDecimal(), Warp.Linear)
        val knob = Knob("Amount", ctrl, spec, flow.context)

        val targetBusSelector = BusSelector()
        targetBusSelector.setFilter(reactiveValue(flow.associatedBus.rate), flow.associatedBus.channels)
        targetBusSelector.syncWith(flow.targetRef)
        targetBusSelector.initialize(flow.context)
        val selectorControl = ObjectSelectorControl(targetBusSelector, createBundle())
        return HBox(10.0, knob, selectorControl).centerChildren()
    }
}