package xenakis.ui.score

import hextant.context.Context
import javafx.geometry.HorizontalDirection
import reaktive.Observer
import reaktive.value.forEach
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.asY
import xenakis.impl.times
import xenakis.impl.zero
import xenakis.model.obj.MeterObject
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject
import xenakis.ui.impl.Direction

class SingleObjectScorePane(
    val rootObj: ScoreObject, context: Context,
) : RootScorePane(rootObj.independentScore(), context) {
    override val displayStart: Decimal
        get() = zero
    override val displayEnd: Decimal
        get() = rootObj.duration

    override val absolutePosition: ObjectPosition
        get() = ObjectPosition(zero, rootObj.liveConfig.yPosition.now)
    private var gridView: TempoGridObjectView? = null
    private lateinit var durationObserver: Observer
    private lateinit var gridObserver: Observer

    override fun initialize() {
        super.initialize()
        gridObserver = rootObj.quantizationConfig.meter.forEach { grid ->
            updateGrid(grid.get())
        }
        durationObserver = rootObj.duration().observe { _ -> repaint() }
    }

    fun getSingleObjectView(): ScoreObjectView? {
        val inst = score.objectInstances.singleOrNull { inst -> inst.obj == rootObj } ?: return null
        return getObjectView(inst)
    }

    override fun repaint() {
        super.repaint()
        resizeGrid()
    }

    private fun resizeGrid() {
        val height = getScoreY(GRID_HEIGHT)
        val direction = Direction.horizontal(HorizontalDirection.RIGHT)
        val gridView = gridView ?: return
        gridView.obj.resize(rootObj.duration, height, ScoreObject.ResizeMode.Regular, direction)
    }

    private fun updateGrid(meter: MeterObject?) {
        if (meter == null) {
            if (gridView != null) {
                children.remove(gridView)
                gridView = null
            }
            return
        }
//        val grid = TempoGridObject(reactiveVariable("helper_${rootObj.name.now}"), meter.reference())
//        grid.initialize(context)
//        val inst = ScoreObjectInstance(grid, zero, 1.0.asY)
//        gridView = TempoGridObjectView(grid, inst)
//        gridView!!.initialize(this)
//        resizeGrid()
//        score.addObject(inst, autoSelect = false)
//        val view = TempoGridObjectView(grid, inst)
//        view.initialize(this)
//        view.prefWidthProperty().bind(widthProperty())
//        view.prefHeight = GRID_HEIGHT
//        view.layoutX = 0.0
//        view.layoutYProperty().bind(heightProperty().subtract(GRID_HEIGHT))
//        gridView = view
//        children.add(view)
    }

    override fun getScoreY(screenY: Double): Decimal = (screenY / (height - GRID_HEIGHT)).asY * rootObj.height

    override fun getScreenY(scoreY: Decimal): Double = ((scoreY / rootObj.height) * (height - GRID_HEIGHT)).value

    override fun isRoot(obj: ScoreObject): Boolean = obj == rootObj || obj == gridView?.obj

    override fun getNearestGrid(position: ObjectPosition): Pair<Decimal, MeterObject>? {
        val fromScore = super.getNearestGrid(position)
        if (fromScore != null) return fromScore
        val config = rootObj.quantizationConfig
        val referenceGrid = config.meter.now.get()
        return if (referenceGrid != null) Pair(zero, referenceGrid)
        else null
    }

    companion object {
        private const val GRID_HEIGHT = 50.0
    }
}