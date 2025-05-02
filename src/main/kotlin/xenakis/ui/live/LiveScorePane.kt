package xenakis.ui.live

import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.zero
import xenakis.model.live.LiveLoopObject
import xenakis.model.score.ObjectPosition
import xenakis.model.score.TempoGridObject
import xenakis.ui.score.RootScorePane

class LiveScorePane(private val obj: LiveLoopObject) : RootScorePane(obj.rootScore, obj.context) {
    override val displayStart: Decimal
        get() = zero
    override val displayEnd: Decimal
        get() = obj.config.computeDuration()

    override fun getNearestGrid(position: ObjectPosition): Pair<Decimal, TempoGridObject>? {
        val fromScore = super.getNearestGrid(position)
        if (fromScore != null) return fromScore
        val config = obj.config
        val referenceGrid = config.grid.now.get()
        return if (config.enableSnapping.now && referenceGrid != null) Pair(zero, referenceGrid)
        else null
    }
}