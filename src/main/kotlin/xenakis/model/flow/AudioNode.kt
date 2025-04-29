package xenakis.model.flow

import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.now
import xenakis.impl.Decimal

sealed interface AudioNode: Comparable<AudioNode> {
    val superColliderName: ReactiveString

    val yPosition: ReactiveValue<Decimal>

    override fun compareTo(other: AudioNode): Int = compareValuesBy(this, other) { node -> node.yPosition.now }
}