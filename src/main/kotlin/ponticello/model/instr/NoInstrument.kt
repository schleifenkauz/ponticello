package ponticello.model.instr

import javafx.scene.paint.Color
import ponticello.model.obj.AbstractContextualObject
import ponticello.sc.client.ScWriter
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable
import java.util.*

class NoInstrument : InstrumentObject, AbstractContextualObject() {
    override val color: ReactiveVariable<Color>
        get() = reactiveVariable(Color.GRAY)

    override fun copy(): InstrumentObject = NoInstrument()

    override val superColliderName: String get() = "<none>"

    override val parameters: ParameterDefList get() = ParameterDefList(Collections.unmodifiableList(emptyList()))

    override val name: ReactiveValue<String>
        get() = reactiveValue("<none>")

    override fun activate() {
        throw IllegalStateException()
    }

    override fun ScWriter.createObject() {
    }

    override fun ScWriter.freeObject() {
    }

    override fun sync() {
    }

    override fun instrumentReference(): InstrumentReference = InstrumentReference.None
}