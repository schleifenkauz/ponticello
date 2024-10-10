package xenakis.ui

import hextant.context.Context
import xenakis.impl.Decimal
import xenakis.impl.asTime
import xenakis.model.ObjectPosition
import xenakis.model.ScoreObject
import xenakis.model.ScoreObjectGroup
import xenakis.model.ScoreObjectInstance

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
    override val pixelsPerSecond: Double
        get() = rootPane.pixelsPerSecond
    override val maxTime: Decimal
        get() = obj.duration
    override val maxY: Decimal
        get() = obj.height

    override val absolutePosition: ObjectPosition
        get() = parentPane.absolutePosition + instance.position

    override val xAccuracy: Int
        get() = rootPane.xAccuracy

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