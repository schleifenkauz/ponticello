package xenakis.ui

import hextant.context.Context
import hextant.fx.registerShortcuts
import javafx.scene.shape.Line
import javafx.scene.text.Text
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.Point
import xenakis.impl.step
import xenakis.model.InteractionSettings.SnapOption
import xenakis.model.Score
import xenakis.ui.XenakisController.Companion.currentProject
import kotlin.math.exp
import kotlin.math.roundToInt

class ScoreView(score: Score, context: Context) : ScorePane(score, context) {
    private val positionTracker = Line() styleClass "mouse-tracker-line"

    private var timeSnap: Double = 0.1
    override val xAccuracy: Int
        get() = accuracy(timeSnap)
    override var displayStart: Double = 0.0
    override var displayEnd: Double = 0.0
    override val pixelsPerSecond: Double
        get() = width / (displayEnd - displayStart)

    override fun snapToGrid(x: Double, y: Double): Point {
        val settings = context[currentProject].settings
        if (!settings.snapEnabled.now) return Point(x, y)
        when (val option = settings.snapOption.now) {
            SnapOption.Seconds -> return Point(getX(getTime(x).roundToInt().toDouble()), y)
            else -> {
                val grids = allViews.filterIsInstance<TempoGridObjectView>()
                val relevantGrids = grids.filter { g -> x in g.layoutX..g.width }
                val nearestGrid = relevantGrids.minByOrNull { g -> g.verticalDist(y) }
                for (grid in grids) {
                    if (grid != nearestGrid) grid.unmark()
                }
                if (nearestGrid == null) return Point(x, y)
                var t = getTime(x)
                t = nearestGrid.obj.snapToGrid(t, option)
                val snappedX = getX(t)
                nearestGrid.mark(snappedX - nearestGrid.layoutX)
                return Point(snappedX, y)
            }
        }
    }

    init {
        listenForEvents()
        score.addListener(this)
        styleClass.add("score-view")
        selectedArea.registerShortcuts {
            on("Alt+S") {
                displayStart = getTime(selectedArea.x)
                displayEnd = getTime(selectedArea.x + selectedArea.width)
                repaint()
            }
        }
    }

    private fun onResize(old: Double, new: Double) {
        if (old != 0.0) {
            val delta = new - old
            displayEnd += getDuration(delta)
        }
        repaint()
    }

    fun displayWholeScore() {
        displayStart = 0.0
        displayEnd = score.objects.maxOfOrNull { obj -> obj.start + obj.duration } ?: 60.0
        widthProperty().addListener { _, old, new -> onResize(old.toDouble(), new.toDouble()) }
        repaint()
    }

    override fun repaint() {
        super.repaint()
        ui.player.repaint()
        displayTimeGrid()
    }

    private fun displayTimeGrid() {
        val settings = context[currentProject].settings
        val gridVisible = settings.displayTimeGrid.asObservableValue()
        var idx = QUANTIZED_PIXELS_PER_SECOND.binarySearchBy(pixelsPerSecond) { s -> s }
        if (idx < 0) idx = (-(idx + 1)).coerceAtMost(QUANTIZED_PIXELS_PER_SECOND.size - 1)
        val quantizedPixelsPerSecond = QUANTIZED_PIXELS_PER_SECOND[idx]
        val gridDist = (1 / quantizedPixelsPerSecond) * 50.0
        timeSnap = getWidth(gridDist) / 10.0
        val accuracy = accuracy(gridDist)
        for (t in displayStart..displayEnd step gridDist) {
            val x = getX(t)
            val l = Line() styleClass "grid-line"
            l.viewOrder = -100.0
            l.startX = x
            l.endX = x
            l.startYProperty().bind(heightProperty().subtract(40))
            l.endYProperty().bind(heightProperty().subtract(5))
            l.visibleProperty().bind(gridVisible)
            children.add(l)
            val timeCode = timeCode(t, accuracy)
            val txt = Text(timeCode).styleClass("grid-time-code")
            txt.x = x - 8
            txt.yProperty().bind(heightProperty().subtract(40))
            txt.visibleProperty().bind(gridVisible)
            children.add(txt)
        }
    }

    override fun listenForEvents() {
        super.listenForEvents()
        setupPositionTracker()
        setupNavigation()
    }

    private fun setupPositionTracker() {
        positionTracker.startYProperty().bind(heightProperty().subtract(40))
        positionTracker.endYProperty().bind(heightProperty().subtract(5))
        positionTracker.viewOrder = 100.0
        setOnMouseEntered { ev ->
            if (!ev.x.isNaN()) {
                positionTracker.layoutX = snapToGrid(ev.x, ev.y).x
                children.add(positionTracker)
                ev.consume()
            }
        }
        setOnMouseMoved { ev ->
            if (!ev.x.isNaN()) {
                positionTracker.layoutX = snapToGrid(ev.x, ev.y).x
                ev.consume()
            }
        }
        setOnMouseExited { ev ->
            children.remove(positionTracker)
            ev.consume()
        }
    }

    private fun setupNavigation() {
        setOnScroll { ev ->
            if (ev.isControlDown) {
                val factor = exp(-ev.deltaY * 0.002)
                zoom(factor, ev.x)
            } else {
                scroll(-ev.deltaX / pixelsPerSecond)
            }
        }
    }

    private fun zoom(amount: Double, evX: Double) {
        val newIntervalSize = (displayEnd - displayStart) * amount
        val oldIntervalCenter = (displayEnd + displayStart) / 2
        val newIntervalCenter = (getTime(evX) + oldIntervalCenter * 3) / 4
        displayStart = newIntervalCenter - (newIntervalSize / 2)
        displayEnd = newIntervalCenter + (newIntervalSize / 2)
        noNegativeTimes()
        repaint()
    }

    private fun scroll(amount: Double) {
        displayStart += amount
        displayEnd += amount
        noNegativeTimes()
        repaint()
    }

    private fun noNegativeTimes() {
        if (displayStart < 0) {
            displayEnd -= displayStart
            displayStart -= displayStart
        }
    }

    companion object {
        private val QUANTIZED_PIXELS_PER_SECOND = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
    }
}