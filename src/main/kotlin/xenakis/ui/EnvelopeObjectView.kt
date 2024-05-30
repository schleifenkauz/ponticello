package xenakis.ui

import bundles.createBundle
import hextant.context.Context
import hextant.context.createControl
import hextant.fx.hbox
import hextant.serial.makeRoot
import reaktive.Observer
import reaktive.value.now
import xenakis.model.BusObject
import xenakis.model.BusRegistry
import xenakis.model.EnvelopeObject
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.createEditor
import xenakis.sc.view.BusSelectorControl

class EnvelopeObjectView(val obj: EnvelopeObject) : ScoreObjectView(obj) {
    override val supportedActions: List<Icon>
        get() = listOf(Icon.Delete, Icon.Repeat, Icon.ExtraWindow)

    private lateinit var busSelector: BusSelector
    private lateinit var busObserver: Observer

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        busSelector = BusSelector(context, preferredRate = Rate.Control, preferredChannels = 1, obj.bus)
        busObserver = busSelector.result.observe { _, _, newBus -> obj.bus = newBus }
        val btn = Icon.Details.button(action = "Edit Envelope configuration") {
            showEnvelopeConfig(context, obj.spec, obj.bus) { spec, output ->
                obj.spec = spec
                obj.bus = output
            }
        }
        header.children.add(1, BusSelectorControl(busSelector, createBundle()))
        header.children.add(1, btn)
    }

    fun updatedSpec() {
        repaintEnvelopes()
    }

    fun updatedBus() {
        busSelector.select(obj.bus)
    }

    companion object {
        fun showEnvelopeConfig(
            context: Context,
            initialSpec: NumericalControlSpec = NumericalControlSpec.DEFAULT,
            initialBus: BusObject = context[BusRegistry].getOutputBus(),
            onConfirm: (spec: NumericalControlSpec, outputBus: BusObject) -> Unit
        ) {
            val specEditor = initialSpec.createEditor(context)
            specEditor.makeRoot()
            val outputBusEditor = BusSelector(context, preferredRate = Rate.Control, preferredChannels = 1, initialBus)
            outputBusEditor.makeRoot()
            val specControl = context.createControl(specEditor)
            val outputBusControl = context.createControl(outputBusEditor)
            val complete = Icon.Check.button(action = "Confirm") {
                specControl.scene.window.hide()
                val spec = specEditor.result.now
                val outputBus = outputBusEditor.result.now
                onConfirm(spec, outputBus)
            }
            val layout = hbox(specControl, outputBusControl, complete) {
                spacing = 5.0
                centerChildrenVertically()
            }
            val window = SubWindow(layout, title = "Envelope config", context, type = SubWindow.Type.Prompt)
            window.width = 1000.0
            window.show()
        }
    }
}