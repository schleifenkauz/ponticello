package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import javafx.scene.paint.Color
import reaktive.value.reactiveVariable
import xenakis.impl.Logger
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.obj.SuperColliderObject
import xenakis.model.obj.SynthDefObject
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.client.SuperColliderListener

class SynthDefRegistry(
    override val objects: MutableList<SynthDefObject>,
) : SuperColliderObjectRegistry<SynthDefObject>(), SuperColliderListener {
    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override val objectType: String
        get() = "SynthDef"

    override fun initialize(context: Context) {
        context[SynthDefRegistry] = this
        super.initialize(context)
        context[SuperColliderClient].addListener(this)
    }

    override fun onMessage(path: String, content: String) {
        if (path.startsWith("/updated")) {
            val type = content.substringBefore(":")
            val name = content.substringAfter(":")
            if (type == "synthdef") {
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

    companion object : PublicProperty<SynthDefRegistry> by publicProperty("InstrumentRegistry") {
        fun createDefault(): SynthDefRegistry =
            SynthDefRegistry(mutableListOf())

        fun defaultInstrument() = ReferencedSynthDefObject("default", reactiveVariable(Color.WHEAT))
    }
}