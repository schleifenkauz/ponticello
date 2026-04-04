package ponticello.ui.score

import fxutils.drag.ConfiguredDropHandler
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import javafx.scene.input.TransferMode
import ponticello.impl.json
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.InstrumentRegistry
import ponticello.model.obj.InstrumentReference
import ponticello.model.registry.reference
import reaktive.value.ReactiveVariable

class InstrumentDropHandler(
    private val variable: ReactiveVariable<InstrumentReference>,
    private val context: Context,
) : ConfiguredDropHandler(json, {
    handleTypedFormat(InstrumentObject.DATA_FORMAT, TransferMode.LINK) { _, ref ->
        val inst = ref.resolve(context[InstrumentRegistry]) ?: return@handleTypedFormat false
        VariableEdit.updateVariable(
            variable, inst.reference(),
            context[UndoManager], "Select instrument"
        )
        true
    }
})