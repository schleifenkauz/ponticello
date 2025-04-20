package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.reactiveVariable
import xenakis.impl.Logger
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.obj.SuperColliderObject
import xenakis.model.obj.SynthDefObject
import xenakis.sc.client.SuperColliderClient

class SynthDefRegistry(
    override val objects: MutableList<SynthDefObject>,
) : SuperColliderObjectRegistry<SynthDefObject>() {
    @Transient
    private lateinit var updateObserver: Observer

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override val objectType: String
        get() = "SynthDef"

    override fun initialize(context: Context) {
        context[SynthDefRegistry] = this
        super.initialize(context)
        updateObserver = context[SuperColliderClient].updatedSynthDef.observe { _, name ->
            val def = getOrNull(name) ?: return@observe
            def.onUpdated()
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