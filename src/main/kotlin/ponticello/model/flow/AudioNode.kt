package ponticello.model.flow

import ponticello.impl.Decimal
import ponticello.model.player.ScorePlayer
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.now

sealed interface AudioNode: Comparable<AudioNode> {
    val startedAt: Decimal

    val player: ScorePlayer? get() = null

    val superColliderName: ReactiveString

    val yPosition: ReactiveValue<Decimal>

    override fun compareTo(other: AudioNode): Int = compareValuesBy(this, other) { node -> node.yPosition.now }
}