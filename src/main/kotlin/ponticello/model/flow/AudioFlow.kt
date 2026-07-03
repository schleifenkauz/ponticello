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
import ponticello.model.score.ScoreObject
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.ui.midi.MidiContext
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
sealed class AudioFlow : AbstractSuperColliderObject() {
    //making this abstract and implementing it in the subclasses is just a workaround
    //the kotlin serialization compiler plugin for some reason
    //fails when there are fields in abstract @Serializable classes
    protected abstract val active: ReactiveVariable<Boolean>
    val isActive: ReactiveValue<Boolean> get() = active

    @Transient
    private var deactivateOnInvalid: Observer? = null

    override fun superColliderName(objectName: String): String = "AudioFlow.get('$objectName')"

    abstract val isValid: ReactiveValue<Boolean>

    @Transient
    var parentGroup: AudioFlowGroup? = null
        private set

    override val registry: AudioFlowGroup.AudioFlowList
        get() = parentGroup!!.flows

    abstract fun writeCode(): String

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
            context[SuperColliderClient].run("$superColliderName.active_($value, notify: false)")
        }
    }

    fun toggleActive() {
        setActive(!isActive.now)
    }

    fun setFlowGroup(group: AudioFlowGroup) {
        parentGroup = group
    }

    fun ScWriter.addToGroup(group: AudioFlowGroup, placement: NodePlacement) {
        setFlowGroup(group)
        append(writeCode())
        if (isActive.now) append(".active_(true, notify: false)")
        append(".create(${placement.code})")
    }

    fun moveToGroup(group: AudioFlowGroup, placement: NodePlacement) {
        parentGroup = group
        client.run("${superColliderName}.node.${placement.moveMethod}(${placement.target})")
    }

    fun ScWriter.free() {
        +"$superColliderName.free"
    }

    protected fun update(message: String) {
        if (parentGroup != null) {
            client.run("$superColliderName.$message")
        }
    }

    override fun ScWriter.freeObject() {}

    override fun sync() {
        val group = parentGroup
        if (group == null) {
            Logger.warn("Attempted to sync $this without a group", Logger.Category.Playback)
            return
        }
        if (!isActive.now || !group.isActive.now) return
        val placement = group.getPlacement(this)
        client.run {
            free()
            addToGroup(group, placement)
        }
    }

    open fun midiContext(): MidiContext? = null

    override fun canRenameTo(newName: String): Boolean =
        context.project.flows.allFlows().none { f -> f.name.now == newName }

    override fun onRename(oldName: String, newName: String) {
        if (parentGroup?.isActive?.now == true) {
            client.run("AudioFlow.rename('$oldName', '$newName')")
        }
    }

    abstract override fun copy(): AudioFlow

    open fun usesBus(bus: BusObject): Boolean = false

    open fun referencesScoreObject(obj: ScoreObject): Boolean = false

    companion object {
        val DATA_FORMAT = TypedDataFormat<FlowReference>("ponticello/audio-flow")
    }
}