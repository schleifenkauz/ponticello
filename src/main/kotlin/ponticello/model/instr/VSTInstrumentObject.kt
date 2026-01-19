package ponticello.model.instr

import hextant.context.Context
import javafx.scene.paint.Color
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.registry.reference
import ponticello.sc.client.ScWriter
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveValue
import reaktive.value.reactiveValue

class VSTInstrumentObject(val flow: VSTPluginFlow) : InstrumentObject {
    override val color: ReactiveValue<Color>
        get() = reactiveValue(Color.BLACK)
    override val parameters: ParameterDefList
        get() = ParameterDefList(mutableListOf())
    override val name: ReactiveValue<String>
        get() = flow.name
    override val context: Context
        get() = flow.context
    override val initialized: Boolean
        get() = flow.initialized

    override fun initialize(context: Context) {
    }

    override fun allParameters(): List<ParameterDefObject> =
        listOf(ParameterDefObject.VELOCITY, ParameterDefObject.CHANNEL)

    override val superColliderName: String get() = "MIDIInstrument(${flow.controllerVar})"

    override fun ScWriter.createObject() {
    }

    override fun ScWriter.freeObject() {
    }

    override fun sync() {
    }

    override fun instrumentReference(): InstrumentReference = InstrumentReference.VST(flow.reference())
}