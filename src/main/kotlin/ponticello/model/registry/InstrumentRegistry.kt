package ponticello.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import hextant.context.Context
import javafx.scene.paint.Color
import ponticello.impl.Logger
import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.ReferencedSynthDefObject
import ponticello.model.obj.SuperColliderObject
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument
import reaktive.value.reactiveVariable

class InstrumentRegistry(
    override val objects: MutableList<InstrumentObject>,
) : SuperColliderObjectRegistry<InstrumentObject>(), OSCMessageListener {
    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override val objectType: String
        get() = "SynthDef"

    override fun initialize(context: Context) {
        context[InstrumentRegistry] = this
        super.initialize(context)
        context[SuperColliderClient].addListener(this)
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        if (event.message.address == "/updated") {
            val type = event.message.getArgument<String>(0, "type") ?: return
            val name = event.message.getArgument<String>(1, "name") ?: return
            if (type == "synth_def") {
                val def = getOrNull(name) ?: return
                def.onUpdated()
            }
        }
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

    companion object : PublicProperty<InstrumentRegistry> by publicProperty("InstrumentRegistry") {
        fun createDefault(): InstrumentRegistry =
            InstrumentRegistry(mutableListOf())

        fun defaultInstrument() = ReferencedSynthDefObject("default", reactiveVariable(Color.WHEAT))
    }
}