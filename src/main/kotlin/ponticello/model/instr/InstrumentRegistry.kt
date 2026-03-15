package ponticello.model.instr

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import javafx.scene.paint.Color
import ponticello.impl.Logger
import ponticello.model.obj.SuperColliderObject
import ponticello.model.registry.SuperColliderObjectRegistry
import reaktive.value.now
import reaktive.value.reactiveVariable

class InstrumentRegistry(
    override val objects: MutableList<InstrumentObject>,
) : SuperColliderObjectRegistry<InstrumentObject>() {
    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override val objectType: String
        get() = "Instrument"

    override fun initialize(context: Context) {
        context[InstrumentRegistry] = this
        super.initialize(context)
    }

    fun synthDescLibContains(name: String): Boolean {
        val answer = client.send("isSynthDef", listOf(name))
        return try {
            answer.get().toBoolean()
        } catch (e: Exception) {
            Logger.error("Failed to query SynthDef '$name'", e)
            false
        }
    }

    override fun getOrNull(name: String) = when (name) {
        MidiInstrument.name.now -> MidiInstrument
        else -> super.getOrNull(name)
    }

    companion object : PublicProperty<InstrumentRegistry> by publicProperty("InstrumentRegistry") {
        fun createDefault(): InstrumentRegistry =
            InstrumentRegistry(mutableListOf())

        fun defaultInstrument() = ReferencedSynthDefObject("default", reactiveVariable(Color.WHEAT))
    }
}