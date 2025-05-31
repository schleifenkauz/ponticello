package ponticello.model.flow

import javafx.scene.input.DataFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.model.obj.AbstractRenamableObject
import ponticello.model.project.flows
import ponticello.model.registry.NamedObject.Companion.NO_NAME
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.midi.MidiContext
import reaktive.Observer
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
sealed class AudioFlow : AbstractRenamableObject() {
    private val active = reactiveVariable(false)
    val isActive: ReactiveValue<Boolean> get() = active

    @Transient
    private var deactivateOnInvalid: Observer? = null

    @SerialName("name")
    override var mutableName = reactiveVariable(NO_NAME)

    val superColliderName get() = getSuperColliderName(name.now)

    abstract val isValid: ReactiveValue<Boolean>

    abstract fun writeCode(placement: NodePlacement): String

    fun activate() {
        if (!isValid.now) {
            Logger.warn("Attempted to activate invalid AudioFlow $this", Logger.Category.Playback)
            return
        }
        active.now = true
        if (deactivateOnInvalid == null) {
            deactivateOnInvalid = isValid.observe { _, _, valid ->
                if (!valid) deactivate()
            }
        }
    }

    fun deactivate() {
        active.now = false
    }

    fun sync() {
        if (!isActive.now) return
        val placement = NodePlacement.replace(superColliderName)
        val code = writeCode(placement)
        context[SuperColliderClient].run(code)
    }

    open fun midiContext(): MidiContext? = null

    private fun getSuperColliderName(name: String) =
        if (name == NO_NAME) "~flow_${hashCode()}" else "~flow_$name"

    abstract fun getDefaultName(): ReactiveString

    fun getDisplayName(): String = name.now.takeIf { it != NO_NAME } ?: getDefaultName().now

    override fun canRenameTo(newName: String): Boolean =
        context[currentProject].flows.all().none { f -> f.name.now == newName }

    abstract fun copy(): AudioFlow

    companion object {
        val DATA_FORMAT = DataFormat("audio-flow")
    }
}