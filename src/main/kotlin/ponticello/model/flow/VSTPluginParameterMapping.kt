package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.model.instr.BusObject
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.BusReference
import ponticello.model.registry.reference
import ponticello.model.server.BusRegistry
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.SuperColliderContext
import reaktive.Observer
import reaktive.and
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class VSTPluginParameterMapping(
    val name: String,
    val controlBus: ReactiveVariable<BusReference>,
    val active: ReactiveVariable<Boolean> = reactiveVariable(true)
) : AbstractContextualObject() {
    @Transient
    private var observer: Observer? = null

    @Transient
    private lateinit var associatedFlow: VSTPluginFlow

    private val controllerVar get() = associatedFlow.controller

    override fun initialize(context: Context) {
        controlBus.now.resolve(context[BusRegistry])
        super.initialize(context)
    }

    fun applyTo(writer: ScWriter, flow: VSTPluginFlow, controllerName: String = flow.controller) {
        associatedFlow = flow
        if (active.now) {
            writer.map(controlBus.now, controllerName)
        }
        observer = controlBus.observe { _, _, newBus ->
            context[SuperColliderClient].map(newBus)
        } and active.observe { _, _, isActive ->
            if (isActive) context[SuperColliderClient].map(controlBus.now) else unmap()
        }
    }

    override fun onRemoved() {
        unmap()
        observer?.kill()
        observer = null
    }

    private fun unmap() {
        val bus = controlBus.now.get()
        val value = bus?.let { b -> "${b.superColliderName}.getSynchronous" } ?: "0"
        context[SuperColliderClient].run("$controllerVar.set('$name', $value)")
    }

    private fun SuperColliderContext.map(controlBus: BusReference, controllerName: String = controllerVar) {
        val bus = controlBus.get()
        if (bus != null) {
            run("$controllerVar.map('${name}', ${bus.superColliderName})")
        }
    }

    fun copy() = VSTPluginParameterMapping(name, controlBus.copy(), active.copy())

    companion object {
        fun create(name: String, bus: BusObject): VSTPluginParameterMapping {
            val ref = reactiveVariable(bus.reference())
            return VSTPluginParameterMapping(name, ref)
        }
    }
}