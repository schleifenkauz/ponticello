package xenakis.model.flow

import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.model.player.SuffixManager
import xenakis.model.registry.NamedObject
import xenakis.model.score.ObjectPosition

data class ScoreObjectInfo(
    val absolutePosition: ObjectPosition,
    val suffix: Int,
    val placement: NodePlacement?,
    val cutoff: Decimal,
) {
    fun uniqueName(obj: NamedObject) = SuffixManager.uniqueName(obj.name.now, suffix)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScoreObjectInfo) return false
        return absolutePosition == other.absolutePosition && suffix == other.suffix
    }

    override fun hashCode(): Int {
        var result = absolutePosition.hashCode()
        result = 31 * result + suffix
        return result
    }
}