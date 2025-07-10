package ponticello.ui.score

import fxutils.styleClass
import hextant.context.Context
import javafx.scene.canvas.Canvas
import javafx.scene.shape.Line
import ponticello.impl.Decimal
import ponticello.impl.asY
import ponticello.impl.times
import ponticello.impl.zero
import ponticello.model.obj.MeterObject
import ponticello.model.obj.project
import ponticello.model.player.ScorePlayer
import ponticello.model.project.UIState
import ponticello.model.project.uiState
import ponticello.model.score.ObjectPosition
import ponticello.model.score.Score
import ponticello.model.score.ScoreObject
import ponticello.ui.score.TempoGridObjectView.Companion.GRID_HEIGHT
import reaktive.Observer
import reaktive.and
import reaktive.value.binding.`if`
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import java.util.concurrent.Future

class SingleObjectScorePane(
    val rootObj: ScoreObject, context: Context,
) : RootScorePane(Score.makeScore(rootObj), context) {
    override val displayStart: Decimal
        get() = zero
    override val displayEnd: Decimal
        get() = rootObj.duration

    override val absolutePosition: ObjectPosition
        get() = ObjectPosition(zero, rootObj.liveConfig.yPosition.now)
    private lateinit var durationObserver: Observer
    private lateinit var snapObserver: Observer
    private lateinit var meterObserver: Observer
    private var meterChangeObserver: Observer? = null

    private val gridCanvas = Canvas()
    private val marker = Line() styleClass "grid-marker-line"

    override fun initialize() {
        super.initialize()
        durationObserver = rootObj.duration().observe { _ -> repaint() }
        setupGrid()
        val settings = context.project.uiState
        snapObserver = settings.snapOption.observe { _ -> repaintGrid() }
            .and(settings.snapEnabled.observe { _ -> repaintGrid() })
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _ -> repaint() }
    }

    private fun setupGrid() {
        children.add(gridCanvas)
        gridCanvas.opacityProperty().bind(
            `if`(context[UIState].snapEnabled, then = { 1.0 }, otherwise = { 0.5 }).asObservableValue()
        )
        gridCanvas.layoutYProperty().bind(heightProperty().subtract(GRID_HEIGHT))
        gridCanvas.widthProperty().bind(widthProperty())
        gridCanvas.height = GRID_HEIGHT
        gridCanvas.setOnMouseClicked { ev ->
            val (t, _) = snapToGrid(ev.x, ev.y)
            val player = context[ScorePlayer.CURRENT]
            if (!player.isScheduled.now) {
                player.playHead.movePlayHead(t)
            }
            ev.consume()
        }
        marker.startYProperty().bind(heightProperty().subtract(GRID_HEIGHT))
        marker.endYProperty().bind(heightProperty())
        marker.visibleProperty().bind(context.project.uiState.snapEnabled.asObservableValue())
        marker.isMouseTransparent = true
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

    override fun repaint(): Future<Boolean> {
        val future = super.repaint()
        repaintGrid()
        return future
    }

    private fun repaintGrid() {
        val meter = rootObj.quantizationConfig.meter.now.get() ?: return
        val duration = rootObj.duration.value
        TempoGridObjectView.paintGrid(context, meter, firstBar = 0, duration, gridCanvas)
    }

    override fun mouseExited() {
        super.mouseExited()
        children.remove(marker)
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
        if (marker !in children) children.add(marker)
        marker.startX = getWidth(t)
        marker.endX = getWidth(t)
    }
}