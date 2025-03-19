package xenakis.model.flow

import hextant.context.Context
import javafx.scene.input.DataFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveString
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.obj.AbstractRenamableObject
import xenakis.model.obj.BusObject
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

@Serializable
sealed class AudioFlow : AbstractRenamableObject(), AudioNode {
    val isActive: ReactiveVariable<Boolean> = reactiveVariable(true)

    @Transient
    val isFirst = reactiveVariable(false)

    @Transient
    val isLast = reactiveVariable(false)

    @Transient
    lateinit var associatedBus: BusObject
        private set

    @SerialName("name")
    override var mutableName = reactiveVariable(NO_NAME)

    @Transient
    final override lateinit var superColliderName: ReactiveString
        private set

    open fun initialize(context: Context, bus: BusObject) {
        super.initialize(context)
        associatedBus = bus
        superColliderName = name.map(::getSuperColliderName)
    }

    protected open fun getSuperColliderName(name: String) =
        if (name == NO_NAME) "~flow_${hashCode()}" else "~flow_$name"

    open val canDeactivate: Boolean get() = true

    override val canRename: Boolean
        get() = false

    abstract fun getDefaultName(): String

    override fun canRenameTo(newName: String): Boolean =
        context[currentProject].flows.all().none { f -> f.name.now == newName }

    abstract fun copy(): AudioFlow

    final override fun hashCode(): Int = super.hashCode()

    final override fun equals(other: Any?): Boolean = super.equals(other)

    companion object {
        val DATA_FORMAT = DataFormat("audio-flow")
        const val NO_NAME = "<no name>"
    }
}