package ponticello.ui.flow

import javafx.application.Platform
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontSmoothingType
import javafx.scene.text.FontWeight
import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.model.instr.BusObject
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import kotlin.math.floor

class LevelMeter(
    private val bus: BusObject.AudioBus,
    offset: ReactiveValue<Decimal> = reactiveValue(zero),
    val meterWidth: Double
) : Canvas() {
    private var levels = emptyList<Double>()

    private val observer: Observer

    val nChannels get() = bus.channels.now

    init {
        width = meterWidth * nChannels + TICK_WIDTH + NUMBER_WIDTH
        heightProperty().addListener {
            paintScale()
            updateLevels(levels)
        }
        paintScale()
        observer = bus.getLevels().observe { _, levels ->
            updateLevels(levels.map { lvl -> lvl + offset.now.toDouble() })
        }
    }

    private fun updateLevels(levels: List<Double>) = Platform.runLater {
        this.levels = levels
        for ((ch, lvl) in levels.withIndex()) {
            val x = ch * meterWidth
            graphicsContext2D.fill = Color.gray(0.08)
            graphicsContext2D.fillRect(x, 0.0, meterWidth - 1, height)

            graphicsContext2D.fill = when {
                lvl == Double.NEGATIVE_INFINITY -> continue
                lvl > 0.0 -> Color.RED
                lvl > -6.0 -> Color.YELLOW
                else -> Color.GREEN
            }
            val y = levelToY(lvl)
            graphicsContext2D.fillRect(x, y, meterWidth - 1, height - y)
        }
    }

    private fun paintScale() {
        val xOffset = nChannels * meterWidth
        graphicsContext2D.clearRect(xOffset, 0.0, TICK_WIDTH + NUMBER_WIDTH, height)
        graphicsContext2D.fill = Color.gray(0.5)
        val tickStep = stepPossibilities.firstOrNull { step -> height / (LEVEL_RANGE / step) >= 10.0 } ?: return
        for (lvl in MIN_LEVEL..MAX_LEVEL step tickStep) {
            val y = levelToY(lvl.toDouble())
            graphicsContext2D.fillRect(xOffset + 2, y, TICK_WIDTH - 2, 0.3)
        }
        graphicsContext2D.font = Font.font("Monospaced", FontWeight.EXTRA_LIGHT, 9.0)
        graphicsContext2D.stroke = Color.WHITE
        graphicsContext2D.lineWidth = 0.5
        graphicsContext2D.fontSmoothingType = FontSmoothingType.LCD
        val textStep = stepPossibilities.firstOrNull { step -> height / (LEVEL_RANGE / step) >= 20.0 } ?: return
        for (lvl in MIN_LEVEL..MAX_LEVEL step textStep) {
            val y = floor(levelToY(lvl.toDouble()))
            graphicsContext2D.strokeText(lvl.toString(), xOffset + TICK_WIDTH + 4, y + 4)
        }
    }

    fun levelToY(lvl: Double): Double =
        (height - VERTICAL_PADDING) - ((lvl - MIN_LEVEL) / LEVEL_RANGE) * (height - VERTICAL_PADDING * 2)

    companion object {
        private const val TICK_WIDTH = 8.0
        private const val NUMBER_WIDTH = 24.0
        const val MIN_LEVEL = -60
        const val MAX_LEVEL = 24
        const val LEVEL_RANGE = MAX_LEVEL - MIN_LEVEL
        private const val VERTICAL_PADDING = 5.0
        private val stepPossibilities = listOf(3, 6, 12, 24)
    }
}