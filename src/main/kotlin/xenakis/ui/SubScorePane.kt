package xenakis.ui

import hextant.context.Context
import xenakis.impl.Point
import xenakis.model.ScoreObjectGroup

class SubScorePane(
    private val obj: ScoreObjectGroup,
    context: Context,
    val parent: ScorePane
) : ScorePane(obj.score, context) {
    override val displayStart: Double
        get() = 0.0
    override val displayEnd: Double
        get() = obj.duration
    override val pixelsPerSecond: Double
        get() = parent.pixelsPerSecond

    override fun snapToGrid(x: Double, y: Double): Point {
        var coords = this.localToScreen(x, y)
        coords = parent.screenToLocal(coords)
        coords = parent.snapToGrid(coords.x, coords.y).point2d
        coords = parent.localToScreen(coords)
        coords = this.screenToLocal(coords)
        return Point(coords)
    }

    override fun getNearestGrid(x: Double, y: Double): TempoGridObjectView? {
        var coords = this.localToScreen(x, y)
        coords = parent.screenToLocal(coords)
        return parent.getNearestGrid(coords.x, coords.y)
    }

    override val xAccuracy: Int
        get() = parent.xAccuracy

    init {
        obj.score.addListener(this)
    }

    override fun addTime(location: Double, amount: Double) {
        super.addTime(location, amount)
        obj.duration += amount
    }

    override fun deleteTimeRange(start: Double, end: Double) {
        super.deleteTimeRange(start, end)
        val amount = end - start
        obj.duration -= amount
    }
}