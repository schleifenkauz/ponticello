package ponticello.ui.score

import fxutils.background
import fxutils.styleClass
import hextant.context.Context
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import ponticello.impl.*
import ponticello.model.live.QuantizationConfig
import ponticello.model.obj.project
import ponticello.model.project.UIState
import ponticello.model.project.uiState
import ponticello.model.score.ObjectPosition
import ponticello.model.score.Score
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectGroup
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.misc.PlayHead
import reaktive.Observer
import reaktive.and
import reaktive.value.ReactiveValue
import reaktive.value.binding.`if`
import reaktive.value.binding.map
import reaktive.value.binding.orElse
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import java.util.concurrent.Future

class SingleObjectScorePane(
    val rootObj: ScoreObject, context: Context, playHead: PlayHead,
    private val paintGrid: ReactiveValue<Boolean>, playHeadStyle: String? = null,
) : RootScorePane(Score.makeAuxiliaryScore(rootObj), context, playHead, playHeadStyle = playHeadStyle) {
    override val displayStart: Decimal
        get() = zero(ObjectPosition.TIME_PRECISION)
    override val displayEnd: Decimal
        get() = rootObj.duration

    override val yRange: DecimalRange
        get() = zero..rootObj.height

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
    private lateinit var paintGridObserver: Observer

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
        setupGrid()
        val settings = context.project.uiState
        snapObserver = settings.snapOption.observe { _ -> repaintGrid() }
            .and(settings.snapEnabled.observe { _ -> repaintGrid() })
        paintGridObserver = paintGrid.observe { _, _, paint ->
            if (paint) repaintGrid()
        }
        gridCanvas.visibleProperty().bind(paintGrid.asObservableValue())
        marker.visibleProperty().bind(paintGrid.asObservableValue())
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _ ->
            updatePixelsPerSecond()
            repaint()
        }
    }

    private fun setupGrid() {
        children.addAll(gridCanvas, marker)
        marker.isVisible = false
        gridCanvas.opacityProperty().bind(
            `if`(context[UIState].snapEnabled, then = { 1.0 }, otherwise = { 0.5 }).asObservableValue()
        )
        gridCanvas.layoutYProperty().bind(heightProperty().subtract(TempoGrid.GRID_HEIGHT))
        gridCanvas.widthProperty().bind(widthProperty())
        gridCanvas.height = TempoGrid.GRID_HEIGHT
        gridCanvas.setOnMouseClicked { ev ->
            val (t, _) = snapToGrid(ev.x, ev.y)
            if (playHead.canMoveManually.now) {
                playHead.movePlayHead(t)
            }
            ev.consume()
        }
        marker.startYProperty().bind(heightProperty().subtract(TempoGrid.GRID_HEIGHT))
        marker.endYProperty().bind(heightProperty())
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
        if (!paintGrid.now) return
        gridCanvas.graphicsContext2D.clearRect(0.0, 0.0, gridCanvas.width, gridCanvas.height)
        if (getSingleObjectView()?.tempoGrid != null) return
        val grid = getPaneGrid()
        if (grid == null) {
            marker.visibleProperty().unbind()
            marker.isVisible = false
            return
        }
        grid.paintGrid(pixelsPerSecond)
    }

    override fun mouseExited() {
        super.mouseExited()
        children.remove(marker)
    }

    override fun getScoreY(screenY: Double): Decimal = (screenY / (height)).asY * rootObj.height

    override fun getScreenY(scoreY: Decimal): Double = ((scoreY / rootObj.height) * (height)).value

    override fun isRoot(obj: ScoreObject): Boolean = obj == rootObj

    private fun getPaneGrid(): TempoGrid? {
        val mainScorePosition = positionInMainScore()
        val quant = quantization
        return when {
            quant != null && quant.meter.now.isResolved.now -> {
                val meter = quant.meter.now.force()
                val offset =
                    if (!quant.shiftGrid.now) zero
                    else quant.computeOffset()
                TempoGrid(
                    TempoGrid.GridType.Regular, rootObj,
                    getPosition = { ObjectPosition.ZERO },
                    meter, scale = one, offset = offset,
                    gridCanvas, marker
                )
            }

            mainScorePosition != null -> {
                val mainScoreView = context[PonticelloMainActivity].mainScoreView
                val grid = mainScoreView.getNearestGrid(mainScorePosition) ?: return null
                val offset = mainScorePosition.time - grid.gridStart
                grid.copy(
                    type = TempoGrid.GridType.Regular, scoreObject = rootObj,
                    getPosition = { ObjectPosition.ZERO },
                    offset = offset, canvas = gridCanvas, marker = marker
                )
            }

            else -> return null
        }
    }

    override fun getNearestGrid(position: ObjectPosition): TempoGrid? {
        val fromScore = super.getNearestGrid(position)
        if (fromScore != null) return fromScore
        return getPaneGrid()
    }
}