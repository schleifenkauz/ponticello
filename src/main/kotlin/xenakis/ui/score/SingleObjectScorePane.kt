package xenakis.ui.score

import hextant.context.Context
import javafx.scene.layout.Pane
import reaktive.Observer
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.asY
import xenakis.impl.times
import xenakis.impl.zero
import xenakis.model.obj.MeterObject
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject

class SingleObjectScorePane(
    val rootObj: ScoreObject, context: Context,
) : RootScorePane(rootObj.independentScore(), context) {
    override val displayStart: Decimal
        get() = zero
    override val displayEnd: Decimal
        get() = rootObj.duration

    override val absolutePosition: ObjectPosition
        get() = ObjectPosition(zero, rootObj.liveConfig.yPosition.now)
    private lateinit var durationObserver: Observer

    private val gridArea = Pane()

    override fun initialize() {
        super.initialize()
        durationObserver = rootObj.duration().observe { _ -> repaint() }
        children.add(gridArea)
        gridArea.layoutYProperty().bind(heightProperty().subtract(GRID_HEIGHT))
    }

    fun getSingleObjectView(): ScoreObjectView? {
        val inst = score.objectInstances.singleOrNull { inst -> inst.obj == rootObj } ?: return null
        return getObjectView(inst)
    }

    override fun repaint() {
        super.repaint()
        repaintGrid()
    }

    private fun repaintGrid() {
        gridArea.children.clear()
        val meter = rootObj.quantizationConfig.meter.now.get() ?: return
        val duration = rootObj.duration.value
        TempoGridObjectView.paintGrid(meter, firstBar = 0, duration, gridArea, width, GRID_HEIGHT)
    }

    override fun getScoreY(screenY: Double): Decimal = (screenY / (height - GRID_HEIGHT)).asY * rootObj.height

    override fun getScreenY(scoreY: Decimal): Double = ((scoreY / rootObj.height) * (height - GRID_HEIGHT)).value

    override fun isRoot(obj: ScoreObject): Boolean = obj == rootObj

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