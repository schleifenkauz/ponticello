package xenakis.model.flow

import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.Decimal
import xenakis.model.player.ActiveScoreObject
import xenakis.model.score.SynthObject

data class SynthObjectNode(
    val obj: SynthObject,
    val active: ActiveScoreObject,
) : AudioNode {
    override val isStillActive: Boolean
        get() = active.isStillActive

    override val superColliderName =
        obj.name.map { _ -> active.superColliderName }

    override val yPosition: ReactiveValue<Decimal>
        get() = reactiveValue(active.absolutePosition.y)

    override fun toString(): String = superColliderName.now
}