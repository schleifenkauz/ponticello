package xenakis.ui.score

import hextant.context.Context
import xenakis.impl.Decimal
import xenakis.impl.asTime
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectGroup
import xenakis.model.score.ScoreObjectInstance
import xenakis.ui.impl.Direction

class SubScorePane(
    private val instance: ScoreObjectInstance,
    private val obj: ScoreObjectGroup,
    private val parentPane: ScorePane,
    context: Context
) : ScorePane(obj.score, context) {
    override val displayStart: Decimal
        get() = 0.0.asTime
    override val displayEnd: Decimal
        get() = obj.duration

    override val absolutePosition: ObjectPosition
        get() = parentPane.absolutePosition + instance.position

    override val associatedObject: ScoreObjectGroup
        get() = obj

    init {
        listenForEvents()
        obj.score.addListener(this)
    }

    override fun addTime(location: Decimal, amount: Decimal) {
        super.addTime(location, amount)
        obj.resize(obj.duration + amount, obj.height, ScoreObject.ResizeType.Regular, Direction.NONE)
    }

    override fun deleteTimeRange(start: Decimal, end: Decimal) {
        super.deleteTimeRange(start, end)
        val amount = end - start
        obj.resize(obj.duration - amount, obj.height, ScoreObject.ResizeType.Regular, Direction.NONE)
    }
}