package ponticello.model.flow

import fxutils.drag.TypedDataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.model.instr.BusObject
import ponticello.model.obj.AbstractSuperColliderObject
import ponticello.model.obj.FlowReference
import ponticello.model.obj.project
import ponticello.model.project.flows
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.ui.midi.MidiContext
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.util.concurrent.CompletableFuture

@Serializable
sealed class AudioFlow : AbstractSuperColliderObject() {
    private val active = reactiveVariable(true)
    val isActive: ReactiveValue<Boolean> get() = active

    @Transient
    private var deactivateOnInvalid: Observer? = null

    override fun superColliderName(objectName: String): String = "~flow_${objectName}"

    abstract val isValid: ReactiveValue<Boolean>

    @Transient
    var parentGroup: AudioFlowGroup? = null
        private set

    override val registry: AudioFlowGroup.AudioFlowList
        get() = parentGroup!!.flows

    abstract fun writeCode(placement: NodePlacement): String

    fun setActive(value: Boolean) {
        if (value && initialized) {
            if (!isValid.now) {
                Logger.warn("Attempted to activate invalid AudioFlow $this", Logger.Category.Playback)
                return
            }
            if (deactivateOnInvalid == null) {
                deactivateOnInvalid = isValid.observe { _, _, valid ->
                    if (!valid) setActive(false)
                }
            }
        }
        active.now = value
        if (parentGroup?.isActive?.now == true) {
            setRunning(value)
        }
    }

    fun toggleActive() {
        setActive(!isActive.now)
    }

    open fun setRunning(active: Boolean) {
        context[SuperColliderClient].run("$superColliderName.run($active)")
    }

    fun addToGroup(group: AudioFlowGroup, placement: NodePlacement): CompletableFuture<String> {
        parentGroup = group
        val code = writeCode(placement)
        return context[SuperColliderClient].eval(code, "activating flow ${name.now}")
    }

    fun moveToGroup(group: AudioFlowGroup, placement: NodePlacement) {
        parentGroup = group
        client.run("${superColliderName}.${placement.moveMethod}(${placement.target})")
    }

    fun release() {
        parentGroup = null
        context[SuperColliderClient].run("${superColliderName}.release")
    }

    override fun sync() {
        if (!isActive.now || parentGroup?.isActive?.now != true) return
        context[SuperColliderClient].run("$superColliderName.release")
        val placement = parentGroup!!.getPlacement(this)
        val code = writeCode(placement)
        context[SuperColliderClient].run(code)
    }

    open fun midiContext(): MidiContext? = null

    override fun canRenameTo(newName: String): Boolean =
        context.project.flows.allFlows().none { f -> f.name.now == newName }

    override fun onRename(oldName: String, newName: String) {
        if (parentGroup?.isActive?.now == true) {
            super.onRename(oldName, newName)
        }
    }

    abstract override fun copy(): AudioFlow

    open fun usesBus(bus: BusObject): Boolean = false

    companion object {
        val DATA_FORMAT = TypedDataFormat<FlowReference>("ponticello/audio-flow")
    }
}