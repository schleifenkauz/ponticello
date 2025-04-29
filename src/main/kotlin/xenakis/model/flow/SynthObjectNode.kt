package xenakis.model.flow

import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.Decimal
import xenakis.model.player.ActiveObjectManager
import xenakis.model.score.ObjectPosition
import xenakis.model.score.SynthObject

data class SynthObjectNode(
    val obj: SynthObject,
    val absolutePosition: ObjectPosition,
    val suffix: Int,
) : AudioNode {
    override val superColliderName =
        obj.name.map { name -> "${obj.superColliderPrefix}${ActiveObjectManager.uniqueName(name, suffix)}" }

    override val yPosition: ReactiveValue<Decimal>
        get() = reactiveValue(absolutePosition.y)

    override fun toString(): String = superColliderName.now
}