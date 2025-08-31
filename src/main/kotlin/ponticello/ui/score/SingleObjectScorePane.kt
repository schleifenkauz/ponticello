package ponticello.ui.score

import fxutils.background
import fxutils.styleClass
import hextant.context.Context
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import ponticello.impl.*
import ponticello.model.live.QuantizationConfig
import ponticello.model.obj.MeterObject
import ponticello.model.obj.project
import ponticello.model.project.UIState
import ponticello.model.project.uiState
import ponticello.model.score.ObjectPosition
import ponticello.model.score.Score
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectGroup
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.misc.PlayHead
import ponticello.ui.score.TempoGridObjectView.Companion.GRID_HEIGHT
import reaktive.Observer
import reaktive.and
import reaktive.value.binding.`if`
import reaktive.value.binding.map
import reaktive.value.binding.orElse
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import java.util.concurrent.Future

class SingleObjectScorePane(
    val rootObj: ScoreObject, context: Context, playHead: PlayHead,
    private val paintGrid: Boolean, playHeadStyle: String? = null,
) : RootScorePane(Score.makeScore(rootObj), context, playHead, playHeadStyle = playHeadStyle) {
    override val displayStart: Decimal
        get() = zero(ObjectPosition.TIME_PRECISION)
    override val displayEnd: Decimal
        get() = rootObj.duration

    private val gridHeight = if (paintGrid) GRID_HEIGHT else 0.0

    var positionInMainScore: () -> ObjectPosition? = { null }
        set(value) {
            field = value
            if (quantization != null) quantization = null
            repaintGrid()
        }

    var quantization: QuantizationConfig? = null
        set(value) {
            field = value
            if (positionInMainScore() != null) positionInMainScore = { null }
            repaintGrid()
        }

    override val absolutePosition: ObjectPosition
        get() = ObjectPosition(
            zero(ObjectPosition.TIME_PRECISION),
            positionInMainScore()?.y ?: zero(ObjectPosition.TIME_PRECISION)
        )
    private lateinit var durationObserver: Observer
    private lateinit var snapObserver: Observer

    private val gridCanvas = Canvas()
    private val marker = Line() styleClass "grid-marker-line"

    override fun initialize() {
        super.initialize()
        if (rootObj is ScoreObjectGroup) {
            backgroundProperty().bind(
                rootObj.associatedColor.orElse(Color.BLACK).map(::background).asObservableValue()
            )
        }
        durationObserver = rootObj.duration().observe { _ -> repaint() }
        if (paintGrid) {
            setupGrid()
            val settings = context.project.uiState
            snapObserver = settings.snapOption.observe { _ -> repaintGrid() }
                .and(settings.snapEnabled.observe { _ -> repaintGrid() })
        }
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _ ->
            updatePixelsPerSecond()
            repaint()
        }
    }

    private fun setupGrid() {
        children.add(gridCanvas)
        gridCanvas.opacityProperty().bind(
            `if`(context[UIState].snapEnabled, then = { 1.0 }, otherwise = { 0.5 }).asObservableValue()
        )
        gridCanvas.layoutYProperty().bind(heightProperty().subtract(gridHeight))
        gridCanvas.widthProperty().bind(widthProperty())
        gridCanvas.height = gridHeight
        gridCanvas.setOnMouseClicked { ev ->
            val (t, _) = snapToGrid(ev.x, ev.y)
            if (playHead.canMoveManually.now) {
                playHead.movePlayHead(t)
            }
            ev.consume()
        }
        marker.startYProperty().bind(heightProperty().subtract(gridHeight))
        marker.endYProperty().bind(heightProperty())
        marker.visibleProperty().bind(context.project.uiState.snapEnabled.asObservableValue())
        marker.isMouseTransparent = true
    }

    fun getSingleObjectView(): ScoreObjectView? {
        val inst = score.objectInstances.singleOrNull { inst -> inst.obj == rootObj } ?: return null
        return getObjectView(inst)
    }

    override fun repaint(): Future<Boolean> {
        updatePixelsPerSecond()
        val future = super.repaint()
        repaintGrid()
        return future
    }

    fun repaintGrid() {
        if (!paintGrid) return
        val mainScorePosition = positionInMainScore()
        val quant = quantization
        val (meter, firstBar, offset) = when {
            quant != null && quant.meter.now.isResolved.now -> {
                val meter = quant.meter.now.force()
                val offset =
                    if (!quant.shiftGrid.now) zero
                    else quant.computeOffset()
                Triple(meter, 0, offset)
            }

            mainScorePosition != null -> {
                val mainScoreView = context[PonticelloMainActivity].mainScoreView
                val (start, meter, firstBar) = mainScoreView.getNearestGrid(mainScorePosition) ?: return
                val offset = mainScorePosition.time - start
                Triple(meter, firstBar, offset)
            }

            else -> return
        }
        val duration = rootObj.duration.value
        TempoGridObjectView.paintGrid(
            context, meter, duration, gridCanvas,
            firstBar, offset, scale = one,
            style = TempoGridObjectView.GridStyle.Regular
        )
    }

    override fun mouseExited() {
        super.mouseExited()
        children.remove(marker)
    }

    override fun getScoreY(screenY: Double): Decimal = (screenY / (height - gridHeight)).asY * rootObj.height

    override fun getScreenY(scoreY: Decimal): Double = ((scoreY / rootObj.height) * (height - gridHeight)).value

    override fun isRoot(obj: ScoreObject): Boolean = obj == rootObj

    override fun snapToGrid(position: ObjectPosition): ObjectPosition {
        val positionInMainScore = positionInMainScore() ?: return super.snapToGrid(position)
        return super.snapToGrid(position + positionInMainScore) - positionInMainScore
    }

    override fun getNearestGrid(position: ObjectPosition): Triple<Decimal, MeterObject, Int>? {
        val fromScore = super.getNearestGrid(position)
        if (fromScore != null) return fromScore
        val mainScorePosition = positionInMainScore()
        val quant = quantization
        return when {
            quant != null && quant.meter.now.isResolved.now -> {
                val meterStart = -quant.computeOffset()
                val firstBar = 0
                Triple(meterStart, quant.meter.now.force(), firstBar)
            }

            mainScorePosition != null -> {
                val mainScoreView = context[PonticelloMainActivity].mainScoreView
                mainScoreView.getNearestGrid(position)
            }

            else -> null
        }
    }

    override fun markT(t: Decimal) {
        super.markT(t)
        if (marker !in children) children.add(marker)
        marker.startX = getWidth(t)
        marker.endX = getWidth(t)
    }
}