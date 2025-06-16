package ponticello.ui.score

import fxutils.Direction
import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.asTime
import ponticello.impl.div
import ponticello.impl.times
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.ScoreObjectInstance

class SubScorePane(
    private val instance: ScoreObjectInstance,
    private val obj: ScoreObjectGroup,
    private val parentPane: ScorePane,
    context: Context,
) : ScorePane(obj.score, context) {
    override val root: ScorePane
        get() = parentPane.root
    override val displayStart: Decimal
        get() = 0.0.asTime
    override val displayEnd: Decimal
        get() = obj.duration

    override val pixelsPerSecond: Double
        get() = parentPane.pixelsPerSecond

    override val absolutePosition: ObjectPosition
        get() = parentPane.absolutePosition + instance.position

    override val associatedObject: ScoreObjectGroup
        get() = obj

    fun initialize() {
        listenForEvents()
        obj.score.addListener(this)
    }

    override fun getScreenY(scoreY: Decimal): Double = scoreY.value * (this.prefHeight / obj.height.value)

    override fun getScoreY(screenY: Double): Decimal = screenY * (obj.height / this.prefHeight)

    override fun addTime(location: Decimal, amount: Decimal) {
        super.addTime(location, amount)
        obj.resize(obj.duration + amount, obj.height, ScoreObject.ResizeMode.Regular, Direction.NONE)
    }

    override fun deleteTimeRange(start: Decimal, end: Decimal) {
        super.deleteTimeRange(start, end)
        val amount = end - start
        obj.resize(obj.duration - amount, obj.height, ScoreObject.ResizeMode.Regular, Direction.NONE)
    }
}