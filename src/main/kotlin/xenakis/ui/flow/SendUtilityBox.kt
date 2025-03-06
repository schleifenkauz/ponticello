package xenakis.ui.flow

import bundles.createBundle
import fxutils.centerChildren
import javafx.scene.Node
import javafx.scene.layout.HBox
import reaktive.value.ReactiveString
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.toDecimal
import xenakis.model.flow.SendFlow
import xenakis.model.registry.ObjectReference
import xenakis.model.score.KnobControl
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.sc.editor.BusSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.controls.Knob

class SendUtilityBox(flow: SendFlow) : FlowBox<SendFlow>(flow) {
    override fun getContent(flow: SendFlow): Node {
        val ctrl = KnobControl(flow.amountPercent)
        val spec = NumericalControlSpec(100.0, 0.0, 100.0, 1.toDecimal(), Warp.Linear)
        val knob = Knob("Amount", ctrl, spec, flow.context)
        val targetBusSelector = BusSelector(
            flow.context,
            preferredRate = flow.associatedBus.rate.now,
            preferredChannels = flow.associatedBus.channels.now,
            flow.targetRef as ReactiveVariable<ObjectReference?>
        )
        val selectorControl = ObjectSelectorControl(targetBusSelector, createBundle())
        return HBox(10.0, knob, selectorControl).centerChildren()
    }

    override fun getTitle(flow: SendFlow): ReactiveString = reactiveValue("Send")
}