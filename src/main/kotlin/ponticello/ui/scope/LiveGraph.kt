package ponticello.ui.scope

import javafx.application.Platform
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import ponticello.impl.Decimal
import ponticello.impl.toDecimal
import ponticello.sc.Transformation

class LiveGraph(
    private val transformation: () -> Transformation,
    private var interval: Double
) : Canvas() {
    private val samples = ArrayDeque<Sample>(1000)
    val currentValues: Sequence<Decimal> get() = samples.asSequence().map { s -> s.value.toDecimal() }
    private var currentTime = 0.0

    val targetRange get() = (height - VERTICAL_OFFSET)..VERTICAL_OFFSET

    init {
        heightProperty().addListener { _, _, _ -> redraw() }
        widthProperty().addListener { _, _, _ -> redraw() }
    }

    private fun redraw() {
        graphicsContext2D.fill = Color.BLACK
        graphicsContext2D.fillRect(0.0, 0.0, width, height)
        graphicsContext2D.stroke = Color.GREEN
        val yMapping = transformation()
        for ((s1, s2) in samples.zipWithNext()) {
            val x1 = timeToWidth(s1.time)
            val y1 = yMapping.map(s1.value)
            val x2 = timeToWidth(s2.time)
            val y2 = yMapping.map(s2.value)
            graphicsContext2D.strokeLine(x1, y1, x2, y2)
        }
    }

    private fun timeToWidth(time: Double): Double = (time - samples.first().time) / interval * width

    fun setInterval(interval: Double) {
        this.interval = interval
        dropOldSamples()
        redraw()
    }

    fun receive(value: Double, time: Double) = Platform.runLater {
        samples.addLast(Sample(time, value))
        currentTime = time
        dropOldSamples()
        redraw()
    }

    private fun dropOldSamples() {
        while (samples.first().time < currentTime - interval) {
            samples.removeFirst()
        }
    }

    private data class Sample(val time: Double, val value: Double)

    companion object {
        private const val VERTICAL_OFFSET = 1.0

        const val DEFAULT_INTERVAL = 10.0
        const val DEFAULT_LAG = 0.0
        const val DEFAULT_RATE = 20.0
    }
}