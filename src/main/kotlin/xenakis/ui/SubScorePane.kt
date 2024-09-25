package xenakis.ui

import hextant.context.Context
import javafx.geometry.Point2D
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

    override val rootPaneHeight: Double
        get() = parent.rootPaneHeight

    private fun translateToParent(x: Double, y: Double): Point2D {
        var coords = this.localToScreen(x, y)
        coords = parent.screenToLocal(coords)
        return coords
    }

    private fun translateFromParent(coords: Point2D): Point2D {
        var coords1 = coords
        coords1 = parent.localToScreen(coords1)
        coords1 = this.screenToLocal(coords1)
        return coords1
    }

    override fun getGrids(x: Double): List<TempoGridObjectView> {
        val coords = translateToParent(x, 0.0)
        return parent.getGrids(coords.x)
    }

    override fun markX(x: Double) {
        val coords = translateToParent(x, 0.0)
        parent.markX(coords.x)
    }

    override fun snapToGrid(x: Double, y: Double): Point {
        var coords = translateToParent(x, y)
        coords = parent.snapToGrid(coords.x, coords.y).point2d
        coords = translateFromParent(coords)
        return Point(coords)
    }

    override fun getNearestGrid(x: Double, y: Double): TempoGridObjectView? {
        val coords = translateToParent(x, y)
        return parent.getNearestGrid(coords.x, coords.y)
    }

    override val xAccuracy: Int
        get() = parent.xAccuracy

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