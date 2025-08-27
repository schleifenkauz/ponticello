package ponticello.model.obj

import javafx.scene.paint.Color
import ponticello.sc.client.ScWriter
import ponticello.ui.registry.ParameterDefList
import reaktive.value.*
import java.util.*

class NoInstrument : InstrumentObject, AbstractContextualObject() {
    override val isAdded: ReactiveBoolean
        get() = reactiveValue(false)

    override val color: ReactiveVariable<Color>
        get() = reactiveVariable(Color.GRAY)

    override fun copy(): InstrumentObject = NoInstrument()

    override val superColliderName: String
        get() = "<none>"

    override val parameters: ParameterDefList get() = ParameterDefList(Collections.unmodifiableList(emptyList()))

    override val name: ReactiveValue<String>
        get() = reactiveValue("<none>")

    override fun onLoadedIntoRegistry() {
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