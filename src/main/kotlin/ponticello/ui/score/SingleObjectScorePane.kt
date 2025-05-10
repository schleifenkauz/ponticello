package ponticello.ui.score

import fxutils.styleClass
import hextant.context.Context
import javafx.scene.layout.Pane
import javafx.scene.shape.Line
import reaktive.Observer
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import ponticello.impl.Decimal
import ponticello.impl.asY
import ponticello.impl.times
import ponticello.impl.zero
import ponticello.model.obj.MeterObject
import ponticello.model.player.ScorePlayer
import ponticello.model.project.settings
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject

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
    private lateinit var meterObserver: Observer
    private var meterChangeObserver: Observer? = null

    private val gridArea = Pane()
    private val marker = Line() styleClass "grid-marker-line"

    override fun initialize() {
        super.initialize()
        durationObserver = rootObj.duration().observe { _ -> repaint() }
        setupGrid()
    }

    private fun setupGrid() {
        children.add(gridArea)
        gridArea.layoutYProperty().bind(heightProperty().subtract(GRID_HEIGHT))
        gridArea.setOnMouseClicked { ev ->
            val (t, _) = snapToGrid(ev.x, ev.y)
            val player = context[ScorePlayer.CURRENT]
            if (!player.isScheduled.now) {
                player.playHead.movePlayHead(t)
            }
            ev.consume()
        }
        marker.endY = GRID_HEIGHT
        marker.visibleProperty().bind(context[currentProject].settings.snapEnabled.asObservableValue())
        meterObserver = rootObj.quantizationConfig.meter.forEach { ref ->
            meterChangeObserver?.kill()
            meterChangeObserver = null
            val meter = ref.get() ?: return@forEach
            meterChangeObserver = meter.observe { repaintGrid() }
            repaintGrid()
        }

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

    override fun mouseExited() {
        super.mouseExited()
        gridArea.children.remove(marker)
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

    override fun markT(t: Decimal) {
        super.markT(t)
        if (marker !in gridArea.children) gridArea.children.add(marker)
        marker.startX = getWidth(t)
        marker.endX = getWidth(t)
    }

    companion object {
        private const val GRID_HEIGHT = 50.0
    }
}