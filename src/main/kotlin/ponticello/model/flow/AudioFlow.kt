package ponticello.model.flow

import fxutils.drag.TypedDataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.model.obj.AbstractRenamableObject
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
sealed class AudioFlow : AbstractRenamableObject() {
    private val active = reactiveVariable(true)
    val isActive: ReactiveValue<Boolean> get() = active

    @Transient
    private var deactivateOnInvalid: Observer? = null

    val superColliderName get() = "~flow_${name.now}"

    abstract val isValid: ReactiveValue<Boolean>

    @Transient
    lateinit var parentGroup: AudioFlowGroup
        private set

    fun setParentGroup(parent: AudioFlowGroup) {
        parentGroup = parent
    }

    abstract fun writeCode(placement: NodePlacement): String

    fun setActive(value: Boolean) {
        if (value) {
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
    }

    fun toggleActive() {
        setActive(!isActive.now)
    }

    fun addToServer(placement: NodePlacement): CompletableFuture<String> {
        val code = writeCode(placement)
        return context[SuperColliderClient].eval(code, "activating flow ${name.now}")
    }

    fun removeFromServer() {
        context[SuperColliderClient].run("${superColliderName}.release")
    }

    open fun setRunning(active: Boolean) {
        context[SuperColliderClient].run("$superColliderName.run($active)")
    }

    fun sync() {
        if (!isActive.now || !parentGroup.isActive.now) return
        context[SuperColliderClient].run("$superColliderName.free")
        val placement = parentGroup.getPlacement(this)
        val code = writeCode(placement)
        context[SuperColliderClient].run(code)
    }

    open fun midiContext(): MidiContext? = null

    override fun canRenameTo(newName: String): Boolean =
        context.project.flows.allFlows().none { f -> f.name.now == newName }

    abstract override fun copy(): AudioFlow

    companion object {
        val DATA_FORMAT = TypedDataFormat<FlowReference>("ponticello/audio-flow")
    }
}