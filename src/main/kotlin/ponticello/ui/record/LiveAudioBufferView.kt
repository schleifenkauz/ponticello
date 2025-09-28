package ponticello.ui.record

import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.StackPane
import ponticello.impl.*

abstract class LiveAudioBufferView(initialDisplayRange: DecimalRange) : StackPane() {
    private val canvas = Canvas()
    protected val graphicsContext: GraphicsContext get() = canvas.graphicsContext2D

    var displayRange: DecimalRange = initialDisplayRange
        private set

    val pixelsPerSecond get() = width / displayRange.dur.toDouble()

    init {
        children.add(canvas)
        canvas.widthProperty().bind(widthProperty())
        canvas.heightProperty().bind(heightProperty())
        setupScrollingAndZooming()
    }

    protected abstract fun repaint()

    private fun setupScrollingAndZooming() {
        setOnScroll { ev ->
            val delta = when {
                ev.deltaX != 0.0 -> ev.deltaX
                ev.deltaY != 0.0 -> ev.deltaY
                else -> return@setOnScroll
            }
            if (ev.isControlDown) {
                val factor = kotlin.math.exp(-delta * 0.01)
                zoom(factor, ev.x)
            } else {
                scroll(-delta / pixelsPerSecond)
            }
        }
    }

    fun display(range: DecimalRange) {
        if (range.isEmpty()) {
            Logger.severe("Attempt to display empty time range: $range", Logger.Category.Score)
            return
        }
        displayRange =
            if (displayRange.start >= zero) range
            else range - range.start
        repaint()
    }

    private fun getTime(x: Double) = displayRange.start + (x / pixelsPerSecond).toDecimal()

    fun zoom(amount: Double, evX: Double) {
        val newIntervalSize = (displayRange.dur) * amount
        val oldIntervalCenter = (displayRange.endInclusive + displayRange.start) / 2
        val newIntervalCenter = (getTime(evX) + oldIntervalCenter * 3) / 4
        val newStart = newIntervalCenter - (newIntervalSize / 2)
        val newEnd = newIntervalCenter + (newIntervalSize / 2)
        display(newStart..newEnd)
    }

    fun scroll(amount: Double) {
        display(displayRange + amount.toDecimal())
    }
}