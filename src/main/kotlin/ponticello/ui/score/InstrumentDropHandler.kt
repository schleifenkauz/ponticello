package ponticello.ui.score

import fxutils.drag.ConfiguredDropHandler
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import javafx.scene.input.TransferMode
import ponticello.model.flow.AudioFlow
import ponticello.model.flow.AudioFlows
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.InstrumentReference
import ponticello.model.instr.InstrumentRegistry
import ponticello.model.registry.reference
import reaktive.value.ReactiveVariable

class InstrumentDropHandler(
    private val variable: ReactiveVariable<InstrumentReference>,
    private val context: Context,
) : ConfiguredDropHandler({
    handleTypedFormat(AudioFlow.DATA_FORMAT, TransferMode.LINK) { _, ref ->
        val flow = ref.resolve(context[AudioFlows].allFlows()) ?: return@handleTypedFormat false
        if (flow !is VSTPluginFlow || !flow.supportsMidiInput) return@handleTypedFormat false
        VariableEdit.updateVariable(
            variable, InstrumentReference.VST(flow.reference()),
            context[UndoManager], "Select instrument"
        )
        true
    }
    handleTypedFormat(InstrumentObject.DATA_FORMAT, TransferMode.LINK) { _, ref ->
        val inst = ref.resolve(context[InstrumentRegistry]) ?: return@handleTypedFormat false
        VariableEdit.updateVariable(
            variable, InstrumentReference.UserDefined(inst.reference()),
            context[UndoManager], "Select instrument"
        )
        true
    }
})