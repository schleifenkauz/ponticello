package ponticello.ui.score

import fxutils.SubWindow
import fxutils.runFXWithTimeout
import fxutils.styleClass
import hextant.context.Context
import javafx.application.Platform
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import ponticello.impl.*
import ponticello.model.obj.project
import ponticello.model.obj.withName
import ponticello.model.project.uiState
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.*
import ponticello.ui.impl.Cursors
import ponticello.ui.misc.PlayHead
import reaktive.event.unitEvent
import reaktive.value.now
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future

abstract class RootScorePane(
    score: Score, context: Context,
    val playHead: PlayHead = PlayHead(),
    val timeCodeView: TimeCodeView = TimeCodeView(),
    val playHeadStyle: String? = null,
) : RegularScorePane(score, context) {
    private val positionTracker = Line() styleClass "mouse-tracker-line"

    private var latestRepaintTrigger = 0L
    private val repaint = unitEvent()
    val onRepaint get() = repaint.stream

    private var magnifiedEnvelope: EnvelopeEditor? = null
    private var magnifierWindow: SubWindow? = null

    final override val root: ScorePane
        get() = this
    final override val associatedObject: ScoreObject?
        get() = null
    final override var pixelsPerSecond: Double = Double.NaN
        private set
        get() {
            if (field.isNaN()) updatePixelsPerSecond()
            return field
        }
    override val absolutePosition: ObjectPosition
        get() = ObjectPosition.ZERO

    open fun initialize() {
        listenForEvents()
        cursor = Cursors.CROSS_HAIR
        playHead.attachTo(this)
        this.score.addListener(this)
    }

    override fun listenForEvents() {
        super.listenForEvents()
        isFocusTraversable = true
        setupPositionTracker()
    }

    protected fun updatePixelsPerSecond() {
        pixelsPerSecond = (this.width / (displayEnd - displayStart)).toDouble()
    }

    override fun getScoreY(screenY: Double): Decimal = (screenY / height).asY

    override fun getScreenY(scoreY: Decimal): Double = (scoreY * height).toDouble()

    private fun setupPositionTracker() {
        positionTracker.startY = 5.0
        positionTracker.endYProperty().bind(heightProperty().subtract(5))
        positionTracker.viewOrder = -100.0
        positionTracker.isMouseTransparent = true
        positionTracker.visibleProperty().bind(hoverProperty())
        setOnMouseMoved { ev -> mouseMoved(ev) }
        setOnMouseExited { mouseExited() }
    }

    override fun mouseMoved(ev: MouseEvent) {
        val (t, y) = snapToGrid(ev.x, ev.y)
        markT(t)
        context[ScoreObjectDuplicator].movedCursor(this, t, y)
        ev.consume()
    }

    override fun mouseExited() {
        for (grid in getAllGrids()) {
            grid.unmark()
        }
        timeCodeView.displayTime(playHead.currentTime)
    }

    fun magnifyEnvelope(editor: EnvelopeEditor) {
        val pane = Pane() styleClass "envelope-sub-window"
        val semitransparent = editor.objectView.backgroundColor.now.deriveColor(1.0, 1.0, 1.0, 0.3)
        pane.style = "-fx-background-color: ${semitransparent.toString().replacePrefix("0x", "#")};"
        magnifiedEnvelope = EnvelopeEditor(editor.namedControl, editor.envelope, editor.objectView, pane)
            .also { it.singleEnvelopeMode = true }
        val objName = editor.objectView.obj.name.now
        val title = "Envelope for ${editor.parameterName} of $objName"
        magnifierWindow?.hide()
        magnifierWindow = SubWindow(pane, title, SubWindow.Type.Popup)
        repositionEnvelopeMagnifier()
        magnifierWindow!!.show()
    }

    private fun repositionEnvelopeMagnifier() {
        val editor = magnifiedEnvelope ?: return
        val window = magnifierWindow ?: return
        editor.pane.setPrefSize(editor.objectView.prefWidth, height / 5)
        editor.repaint()
        val coords = editor.objectView.localToScreen(0.0, 0.0) ?: return
        window.sizeToScene()
        window.x = coords.x
        window.y =
            if (coords.y + editor.objectView.prefHeight / 2 > height / 2) coords.y - editor.pane.prefHeight - 10.0
            else coords.y + editor.objectView.prefHeight + 10.0
        window.scene.fill = Color.TRANSPARENT
    }

    private fun layoutObjects(): Future<Boolean> {
        val repaintTrigger = latestRepaintTrigger
        val maxTime = 50L //determines how much time is spent consecutively on the Application Thread
        val itr = views.toMap().iterator()
        val future = CompletableFuture<Boolean>()
        layoutExecutor.execute {
            while (itr.hasNext()) {
                if (repaintTrigger != latestRepaintTrigger) {
                    break
                }
                val job = CompletableFuture<Unit>()
                Platform.runLater { layoutObjects(itr, maxTime, job) }
                job.join()
            }
            future.complete(true)
        }
        return future
    }

    override fun repaint(): Future<Boolean> {
        if (height == 0.0 || width == 0.0) return CompletableFuture.completedFuture(false)
        if (displayEnd - displayStart <= zero) return CompletableFuture.completedFuture(false)
        latestRepaintTrigger = System.currentTimeMillis()
        removeOutOfRangeChildren()
        val future = layoutObjects()
        repositionEnvelopeMagnifier()
        repaint.fire()
        if (positionTracker !in children) children.add(positionTracker)
        playHead.updatePosition()
        return future
    }

    override fun getNearestGrid(position: ObjectPosition): TempoGrid? {
        val grids = getAllGrids()
        val relevantGrids = grids.filter { g ->
            position.time in g.timeRange && g.yPosition > position.y
        }
        return relevantGrids.minByOrNull { g -> g.yPosition }
    }

    private fun getAllGrids(): List<TempoGrid> = allViews.mapNotNull { view -> view.tempoGrid }

    override fun snapToGrid(position: ObjectPosition): ObjectPosition {
        val settings = context.project.uiState
        val (t, y) = position
        if (!settings.snapEnabled.now) return position
        when (val option = settings.snapOption.now) {
            TimeUnit.Seconds -> return ObjectPosition(t.round(0), y)
            else -> {
                val grid = getNearestGrid(position) ?: return position
                val snapped = grid.snapToGrid(t, option)
                return ObjectPosition(snapped, y)
            }
        }
    }

    override fun markT(t: Decimal) {
        val grids = getAllGrids()
        for (g in grids) {
            if (t in g.timeRange) {
                val x = getWidth(t - g.gridStart)
                g.mark(x)
            } else g.unmark()
        }
        positionTracker.layoutX = getX(t)
        if (playHead.canMoveManually.now) {
            timeCodeView.displayTime(t)
        }
    }

    override fun doubleClicked(ev: MouseEvent) {
        ev.consume()
        val defaultName = context[ScoreObjectRegistry].availableName("memo")
        val obj = MemoObject().withName(defaultName)
        val (t, y) = snapToGrid(ev.x, ev.y)
        val inst = ScoreObjectInstance(obj, t, y)
        score.addObject(inst, autoSelect = true)
        val view = getObjectView(inst) as MemoObjectView
        runFXWithTimeout(20) {
            view.enterEdit()
        }
    }

    companion object {
        private val layoutExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "Layout Thread") }
    }
}