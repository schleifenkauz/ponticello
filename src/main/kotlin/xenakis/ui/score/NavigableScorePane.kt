package xenakis.ui.score

import fxutils.SubWindow
import fxutils.styleClass
import hextant.context.Context
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.text.Text
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.*
import xenakis.model.project.UIState.SnapOption
import xenakis.model.project.settings
import xenakis.model.score.*
import xenakis.ui.impl.verticalDist
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlin.math.exp

class NavigableScorePane(score: Score, context: Context) : ScorePane(score, context) {
    private val positionTracker = Line() styleClass "mouse-tracker-line"
    private val timeGridObjects = mutableListOf<Node>()

    override val root: ScorePane
        get() = this

    private var latestRepaintTrigger = 0L

    private var timeGrid: Double = 0.1
    val xAccuracy: Int
        get() = accuracy(timeGrid)
    override var displayStart: Decimal = 0.0.asTime

    override var displayEnd: Decimal = 0.0.asTime

    override val pixelsPerSecond: Double
        get() = (this.width / (displayEnd - displayStart)).toDouble()

    override val associatedObject: ScoreObjectGroup?
        get() = null

    override val absolutePosition: ObjectPosition
        get() = ObjectPosition.ZERO

    val displayedDuration get() = displayEnd - displayStart

    private var magnifiedEnvelope: EnvelopeEditor? = null
    private var magnifierWindow: SubWindow? = null

    init {
        styleClass.add("score-view")
        isFocusTraversable = true
        listenForEvents()
        heightProperty().addListener { _ -> repaint() }
        widthProperty().addListener { _ -> repaint() }
    }

    fun initialize() {
        this.score.addListener(this)
    }

    override fun snapToGrid(position: ObjectPosition): ObjectPosition {
        val settings = context[currentProject].settings
        val (t, y) = position
        if (!settings.snapEnabled.now) return position
        when (val option = settings.snapOption.now) {
            SnapOption.Seconds -> return ObjectPosition(t.round(0), y)
            else -> {
                val nearestGrid = getNearestGrid(position)
                for (grid in allViews.filterIsInstance<TempoGridObjectView>()) {
                    if (grid.instance != nearestGrid) grid.unmark()
                }
                if (nearestGrid == null) return position
                val obj = nearestGrid.obj as TempoGridObject
                val snapped = obj.snapToGrid(t - nearestGrid.start, option)
                return ObjectPosition(snapped + nearestGrid.start, y)
            }
        }
    }

    override fun markT(t: Decimal) {
        val grids = allViews
            .filterIsInstance<TempoGridObjectView>()
        for (g in grids) {
            if (t in g.instance.timeRange) g.mark(t - g.instance.start)
            else g.unmark()
        }
        positionTracker.layoutX = getX(t)
    }

    override fun getNearestGrid(position: ObjectPosition): ScoreObjectInstance? {
        val grids = score.objectInstances.filter { inst -> inst.obj is TempoGridObject }
        val relevantGrids = grids.filter { g -> position.time in g.timeRange }
        val nearestGrid = relevantGrids.minByOrNull { g -> g.verticalDist(position.y) }
        return nearestGrid
    }

    fun magnifyEnvelope(editor: EnvelopeEditor) {
        val pane = Pane() styleClass "envelope-sub-window"
        val semitransparent = editor.objectView.backgroundColor.now.deriveColor(1.0, 1.0, 1.0, 0.3)
        pane.style = "-fx-background-color: ${semitransparent.toString().replacePrefix("0x", "#")};"
        magnifiedEnvelope = EnvelopeEditor(editor.namedControl, editor.envelope, editor.objectView, pane)
        val objName = editor.objectView.instance.obj.name.now
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

    fun displayWholeScore() {
        val totalDuration = score.objectInstances.maxOfOrNull { obj -> obj.start + obj.duration } ?: 60.0.asTime
        display(zero, totalDuration)
    }

    fun display(start: Decimal, end: Decimal) {
        if (end < start) {
            Logger.severe("Attempt to display empty time range: $start .. $end", Logger.Category.Score)
            return
        }
        displayStart = start
        displayEnd = end
        noNegativeTimes()
        repaint()
    }

    private fun noNegativeTimes() {
        if (displayStart < zero) {
            displayEnd -= displayStart
            displayStart -= displayStart
        }
    }

    override fun repaint() {
        latestRepaintTrigger = System.currentTimeMillis()
        layoutObjects()
        repositionEnvelopeMagnifier()
        if (positionTracker !in children) children.add(positionTracker)
        activity.playback.playHead.updatePosition()
        displayTimeGrid()
        context[ScoreObjectDuplicator].repainted(this)
    }

    private fun layoutObjects() {
        val repaintTrigger = latestRepaintTrigger
        val maxTime = 25L //determines how much time is spent consecutively on the Application Thread
        val itr = views.iterator()
        thread {
            while (itr.hasNext()) {
                if (repaintTrigger != latestRepaintTrigger) {
                    break
                }
                val job = CompletableFuture<Unit>()
                Platform.runLater { layoutObjects(itr, maxTime, job) }
                job.join()
            }
        }
    }

    private fun displayTimeGrid() {
        children.removeAll(timeGridObjects)
        timeGridObjects.clear()
        val settings = context[currentProject].settings
        val gridVisible = settings.displayTimeGrid.asObservableValue()
        var idx = QUANTIZED_PIXELS_PER_SECOND.binarySearchBy(pixelsPerSecond) { s -> s }
        if (idx < 0) idx = (-(idx + 1)).coerceAtMost(QUANTIZED_PIXELS_PER_SECOND.size - 1)
        val quantizedPixelsPerSecond = QUANTIZED_PIXELS_PER_SECOND[idx]
        timeGrid = (1 / quantizedPixelsPerSecond) * 50.0
        for (t in displayStart..displayEnd step timeGrid) {
            val x = getX(t)
            val l = Line() styleClass "grid-line"
            l.viewOrder = -100.0
            l.startX = x
            l.endX = x
            l.startYProperty().bind(heightProperty().subtract(40))
            l.endYProperty().bind(heightProperty().subtract(5))
            l.visibleProperty().bind(gridVisible)
            timeGridObjects.add(l)
            val timeCode = timeCode(t, xAccuracy)
            val txt = Text(timeCode).styleClass("grid-time-code")
            txt.x = x - 8
            txt.yProperty().bind(heightProperty().subtract(40))
            txt.visibleProperty().bind(gridVisible)
            timeGridObjects.add(txt)
        }
        children.addAll(timeGridObjects)
    }

    override fun listenForEvents() {
        super.listenForEvents()
        isFocusTraversable = true
        setupPositionTracker()
        setupNavigation()
    }

    private fun setupPositionTracker() {
        positionTracker.startY = 5.0
        positionTracker.endYProperty().bind(heightProperty().subtract(5))
        positionTracker.viewOrder = -100.0
        positionTracker.isMouseTransparent = true
        positionTracker.visibleProperty().bind(hoverProperty())
        setOnMouseMoved { ev ->
            val (t, y) = snapToGrid(ev.x, ev.y)
            markT(t)
            context[ScoreObjectDuplicator].movedCursor(this, t, y)
            ev.consume()
        }
    }

    private fun setupNavigation() {
        setOnScroll { ev ->
            if (ev.isControlDown) {
                val factor = exp(-ev.deltaY * 0.01)
                zoom(factor, ev.x)
            } else {
                scroll(-ev.deltaY / pixelsPerSecond)
            }
        }
    }

    fun zoom(amount: Double, evX: Double) {
        val newIntervalSize = (displayEnd - displayStart) * amount
        val oldIntervalCenter = (displayEnd + displayStart) / 2
        val newIntervalCenter = (getTime(evX) + oldIntervalCenter * 3) / 4
        display(newIntervalCenter - (newIntervalSize / 2), newIntervalCenter + (newIntervalSize / 2))
    }

    fun displaySelectedArea() {
        val area = selectedArea ?: return
        clearRegionSelection()
        display(area.time, area.time + area.duration)
    }

    fun scroll(amount: Double) {
        displayStart += amount
        displayEnd += amount
        display(displayStart + amount, displayEnd + amount)
        repaint()
    }

    companion object {
        private val QUANTIZED_PIXELS_PER_SECOND = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
    }
}