package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.obj.InstrumentObject
import xenakis.model.obj.InstrumentReference
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.obj.SuperColliderObject

@Serializable
class InstrumentRegistry(
    @SerialName("selectedInstrument") private val selectedInstrumentRef: ReactiveVariable<InstrumentReference>,
    override val objects: MutableList<InstrumentObject>
) : SuperColliderObjectRegistry<InstrumentObject>() {
    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot //TODO this doesn't work for VSTPlugins

    override val objectType: String
        get() = "Instrument"

    val selectedInstrument: InstrumentObject?
        get() = selectedInstrumentRef.now.get()

    override fun initialize(context: Context) {
        super.initialize(context)
        context[InstrumentRegistry] = this
        selectedInstrumentRef.now.resolve(this)
    }

    fun select(instrument: InstrumentObject?) {
        selectedInstrumentRef.now = instrument?.reference() ?: ObjectReference.none()
    }

    fun synthDescLibContains(name: String): Boolean {
        val answer = client.send("isSynthDef", listOf(name))
        return answer.get().toBoolean()
    }

    companion object : PublicProperty<InstrumentRegistry> by publicProperty("InstrumentRegistry") {
        fun createDefault(): InstrumentRegistry =
            InstrumentRegistry(reactiveVariable(ObjectReference.none()), mutableListOf())

        fun defaultInstrument() = ReferencedSynthDefObject("default", reactiveVariable(Color.WHEAT))
    }
}