package xenakis.model.flow

import javafx.scene.input.DataFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Logger
import xenakis.model.obj.AbstractRenamableObject
import xenakis.model.project.flows
import xenakis.model.registry.NamedObject.Companion.NO_NAME
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

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

    fun getSuperColliderName(name: String) =
        if (name == NO_NAME) "~flow_${hashCode()}" else "~flow_$name"

    override val canRename: Boolean
        get() = false

    abstract fun getDefaultName(): ReactiveString

    override fun canRenameTo(newName: String): Boolean =
        context[currentProject].flows.all().none { f -> f.name.now == newName }

    abstract fun copy(): AudioFlow

    final override fun hashCode(): Int = super.hashCode()

    final override fun equals(other: Any?): Boolean = super.equals(other)

    companion object {
        val DATA_FORMAT = DataFormat("audio-flow")
    }
}