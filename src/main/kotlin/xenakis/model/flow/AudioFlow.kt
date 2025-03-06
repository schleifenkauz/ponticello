package xenakis.model.flow

import javafx.scene.input.DataFormat
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveString
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.BusObject

@Serializable
sealed class AudioFlow : AbstractContextualObject(), AudioNode {
    val isActive: ReactiveVariable<Boolean> = reactiveVariable(true)

    val index: ReactiveVariable<Int> = reactiveVariable(0)

    abstract val associatedBus: BusObject

    abstract override val superColliderName: ReactiveString

    abstract fun copyFor(associatedBus: BusObject): AudioFlow

    companion object {
        val DATA_FORMAT = DataFormat("audio-flow")
    }
}