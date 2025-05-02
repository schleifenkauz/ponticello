package xenakis.model.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.impl.copy

@Serializable
class MeterObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val beatsPerMinute: ReactiveVariable<Int>,
    val beatsPerBar: ReactiveVariable<Int>,
    val ticksPerBeat: ReactiveVariable<Int>,
) : AbstractRenamableObject() {
    override val canCopy: Boolean
        get() = true

    override fun copy(name: String) = MeterObject(
        reactiveVariable(name),
        beatsPerMinute.copy(), beatsPerBar.copy(), ticksPerBeat.copy()
    )
}