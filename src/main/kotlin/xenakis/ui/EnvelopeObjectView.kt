package xenakis.ui

import bundles.createBundle
import hextant.context.Context
import hextant.context.createControl
import hextant.fx.hbox
import hextant.serial.makeRoot
import reaktive.value.now
import xenakis.model.EnvelopeObject
import xenakis.sc.NumericalControlSpec
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.createEditor
import xenakis.sc.view.ObjectSelectorControl

class EnvelopeObjectView(val obj: EnvelopeObject) : ScoreObjectView(obj) {
    override val supportedActions: List<Icon>
        get() = listOf(Icon.Delete, Icon.Repeat, Icon.ExtraWindow)

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        addAction(Icon.Details, action = "Edit Envelope configuration") {
            showEnvelopeConfig(context, obj.busSelector, obj.spec) { spec -> obj.spec = spec }
        }
        val selectorControl = ObjectSelectorControl(obj.busSelector, createBundle())
        detailPane.addItem("Output bus: ", selectorControl)
    }

    fun updatedSpec() {
        repaintEnvelopes()
    }

    companion object {
        fun showEnvelopeConfig(
            context: Context,
            busSelector: BusSelector,
            initialSpec: NumericalControlSpec = NumericalControlSpec.DEFAULT,
            onConfirm: (spec: NumericalControlSpec) -> Unit
        ) {
            val specEditor = initialSpec.createEditor(context)
            specEditor.makeRoot()
            val specControl = context.createControl(specEditor)
            val outputBusControl = ObjectSelectorControl(busSelector, createBundle())
            val complete = Icon.Check.button(action = "Confirm") {
                specControl.scene.window.hide()
                val spec = specEditor.result.now
                onConfirm(spec)
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