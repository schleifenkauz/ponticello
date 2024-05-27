package xenakis.ui

import hextant.context.Context
import javafx.scene.shape.Line
import javafx.scene.text.Text
import xenakis.impl.step
import xenakis.model.Score
import kotlin.math.exp

class ScoreView(score: Score, context: Context) : ScorePane(score, context) {
    private val positionTracker = Line() styleClass "mouse-tracker-line"

    override var timeSnap: Double = 0.1
    override var displayStart: Double = 0.0
    override var displayEnd: Double = 0.0
    override val pixelsPerSecond: Double
        get() = width / (displayEnd - displayStart)

    init {
        listenForEvents()
        score.addListener(this)
        styleClass.add("score-view")
    }

    private fun onResize(old: Double, new: Double) {
        val delta = new - old
        displayEnd += getDuration(delta)
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
        var idx = QUANTIZED_PIXELS_PER_SECOND.binarySearchBy(pixelsPerSecond) { s -> s }
        if (idx < 0) idx = (-(idx + 1)).coerceAtMost(QUANTIZED_PIXELS_PER_SECOND.size - 1)
        val quantizedPixelsPerSecond = QUANTIZED_PIXELS_PER_SECOND[idx]
        val gridDist = (1 / quantizedPixelsPerSecond) * 50.0
        timeSnap = getWidth(gridDist) / 10.0
        val accuracy = accuracy(gridDist)
        for (t in displayStart..displayEnd step gridDist) {
            val x = getX(t)
            val l = Line(x, 20.0, x, height - 40.0).styleClass("grid-line")
            l.endYProperty().bind(heightProperty().subtract(40))
            children.add(l)
            val timeCode = timeCode(t, accuracy)
            val txt = Text(timeCode).styleClass("grid-time-code")
            txt.relocate(x - 8, height - 20)
            txt.layoutYProperty().bind(heightProperty().subtract(20))
            children.add(txt)
        }
    }

    override fun listenForEvents() {
        super.listenForEvents()
        setupPositionTracker()
        setupNavigation()
    }

    private fun setupPositionTracker() {
        positionTracker.startY = 10.0
        positionTracker.endYProperty().bind(heightProperty().subtract(10))
        positionTracker.viewOrder = 1.0
        setOnMouseEntered { ev ->
            if (!ev.x.isNaN()) {
                positionTracker.layoutX = ev.x.snap(timeSnap)
                children.add(positionTracker)
                ev.consume()
            }
        }
        setOnMouseMoved { ev ->
            if (!ev.x.isNaN()) {
                positionTracker.layoutX = ev.x.snap(timeSnap)
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