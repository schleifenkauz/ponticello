package xenakis.model.obj

import javafx.scene.paint.Color
import reaktive.value.*
import xenakis.sc.client.ScWriter

class NoSynthDef : SynthDefObject, AbstractContextualObject() {
    override val isAdded: ReactiveBoolean
        get() = reactiveValue(false)

    override val color: ReactiveVariable<Color>
        get() = reactiveVariable(Color.GRAY)

    override fun copy(name: String): SynthDefObject = NoSynthDef()

    override val parameters: List<ParameterDefObject> get() = emptyList()

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
}