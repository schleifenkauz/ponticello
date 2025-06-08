package ponticello.model.flow

import javafx.scene.input.DataFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.project.flows
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.midi.MidiContext
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
sealed class AudioFlow : AbstractRenamableObject() {
    private val active = reactiveVariable(false)
    val isActive: ReactiveValue<Boolean> get() = active

    @Transient
    private var deactivateOnInvalid: Observer? = null

    val superColliderName get() = "~flow_${name.now}"

    abstract val isValid: ReactiveValue<Boolean>

    @Transient
    lateinit var parentGroup: AudioFlowGroup
        private set

    fun setParentGroup(parent: AudioFlowGroup) {
        check(!initialized) { "Parent group of $this cannot be changed after initialization" }
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

    fun addToServer(placement: NodePlacement) {
        val code = writeCode(placement)
        //join enforces that the synths are added in the right order
        context[SuperColliderClient].eval(code, "activating flow ${name.now}").join()
    }

    fun removeFromServer() {
        context[SuperColliderClient].run("${superColliderName}.release")
    }

    open fun setRunning(active: Boolean) {
        context[SuperColliderClient].run("$superColliderName.run($active)")
    }

    fun sync() {
        if (!isActive.now) return
        val placement = NodePlacement.replace(superColliderName)
        val code = writeCode(placement)
        context[SuperColliderClient].run(code)
    }

    open fun midiContext(): MidiContext? = null

    override fun canRenameTo(newName: String): Boolean =
        context[currentProject].flows.allFlows().none { f -> f.name.now == newName }

    abstract override fun copy(): AudioFlow

    companion object {
        val DATA_FORMAT = DataFormat("audio-flow")
    }
}