package xenakis.ui

import bundles.createBundle
import hextant.context.Context
import hextant.context.createControl
import hextant.fx.vbox
import hextant.serial.makeRoot
import javafx.scene.layout.VBox
import reaktive.value.now
import xenakis.model.EnvelopeObject
import xenakis.model.ScoreObjectInstance
import xenakis.sc.NumericalControlSpec
import xenakis.sc.editor.BusSelector
import xenakis.sc.editor.createEditor
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.prompt.Prompt

class EnvelopeObjectView(inst: ScoreObjectInstance, val obj: EnvelopeObject) : ScoreObjectView(inst) {
    override val supportedActions: List<Icon>
        get() = listOf(Icon.Delete, Icon.Repeat, Icon.ExtraWindow)

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        addAction(Icon.Details, action = "Edit Envelope configuration") {
            obj.spec = showEnvelopeConfig(
                context, "Edit Envelope configuration of ${obj.name.now}",
                obj.busSelector, obj.spec
            ) ?: return@addAction
        }
    }

    override fun DetailPane.setupDetailPane() {
        val selectorControl = ObjectSelectorControl(obj.busSelector, createBundle())
        addItem("Output bus: ", selectorControl)
    }

    fun updatedSpec() {
        repaintEnvelopes()
    }

    class ControlSpecInput(
        override val title: String,
        busSelector: BusSelector, initialSpec: NumericalControlSpec, context: Context
    ) : Prompt<NumericalControlSpec?, VBox>() {
        private val specEditor = initialSpec.createEditor(context).also { editor -> editor.makeRoot() }
        private val specControl = context.createControl(specEditor)
        private val outputBusControl = ObjectSelectorControl(busSelector, createBundle())
        private val complete = Icon.Check.button(action = "Confirm") {
            val spec = specEditor.result.now
            commit(spec)
        }
        override val content: VBox = vbox(specControl, outputBusControl, complete) {
            spacing = 5.0
            centerChildrenVertically()
        }

        override fun getDefault(): NumericalControlSpec? = null
    }

    companion object {
        fun showEnvelopeConfig(
            context: Context,
            title: String,
            busSelector: BusSelector,
            initialSpec: NumericalControlSpec = NumericalControlSpec.DEFAULT,
        ): NumericalControlSpec? {
            val input = ControlSpecInput(title, busSelector, initialSpec, context)
            return input.showDialog(context)
        }
    }
}