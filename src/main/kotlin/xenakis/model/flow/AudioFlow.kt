package xenakis.model.flow

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveString
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.BusObject
import xenakis.sc.client.ScWriter

@Serializable
sealed class AudioFlow : AbstractContextualObject(), Flow {
    val isActive: ReactiveVariable<Boolean> = reactiveVariable(true)

    var index: Int = 0

    abstract val associatedBus: BusObject

    abstract val superColliderName: ReactiveString

    abstract fun copyFor(associatedBus: BusObject): AudioFlow

    abstract fun ScWriter.writeCode(synthName: String, order: ScoreObjectInfo)
}