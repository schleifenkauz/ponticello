package xenakis.ui

import hextant.context.Context
import xenakis.impl.Point
import xenakis.model.ScoreObjectGroup

class SubScorePane(private val obj: ScoreObjectGroup, context: Context) : ScorePane(obj.score, context) {
    override val displayStart: Double
        get() = 0.0
    override val displayEnd: Double
        get() = obj.duration
    override val pixelsPerSecond: Double
        get() = rootPane.pixelsPerSecond
    override val maxTime: Double
        get() = obj.duration
    override val maxY: Double
        get() = obj.height
    override val rootPane: ScoreView get() = context[XenakisUI].scoreView

    override fun getGrids(x: Double): List<TempoGridObjectView> {
        val coords = rootPane.translateFrom(this, x, 0.0)
        return rootPane.getGrids(coords.x)
    }

    override fun markX(x: Double) {
        val coords = rootPane.translateFrom(this, x, 0.0)
        rootPane.markX(coords.x)
    }

    override fun snapToGrid(x: Double, y: Double): Point {
        var coords = rootPane.translateFrom(this, x, y)
        coords = rootPane.snapToGrid(coords.x, coords.y)
        coords = rootPane.translateTo(this, coords.x, coords.y)
        return coords
    }

    override fun getNearestGrid(x: Double, y: Double): TempoGridObjectView? {
        val coords = rootPane.translateFrom(this, x, y)
        return rootPane.getNearestGrid(coords.x, coords.y)
    }

    override val xAccuracy: Int
        get() = rootPane.xAccuracy

    init {
        listenForEvents()
        obj.score.addListener(this)
    }

    override fun addTime(location: Double, amount: Double) {
        super.addTime(location, amount)
        obj.resize(obj.duration + amount, obj.height, stretch = false, Direction.NONE)
    }

    override fun deleteTimeRange(start: Double, end: Double) {
        super.deleteTimeRange(start, end)
        val amount = end - start
        obj.resize(obj.duration - amount, obj.height, stretch = false, Direction.NONE)
    }
}