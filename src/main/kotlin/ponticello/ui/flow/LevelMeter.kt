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
import ponticello.model.server.BusLevel
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import kotlin.math.floor

class LevelMeter(
    private val bus: BusObject.AudioBus,
    private val replyId: Int = bus.replyId,
    offset: ReactiveValue<Decimal> = reactiveValue(zero),
    val meterWidth: Double
) : Canvas() {
    private var levels: BusLevel? = null

    private val observer: Observer

    init {
        heightProperty().addListener {
            paintScale()
            levels?.let { level -> updateLevels(level) }
        }
        paintScale()
        observer = bus.registry.levels(replyId).observe { _, levels ->
            updateLevels(levels + offset.now.toDouble())
        }
    }

    private fun updateLevels(level: BusLevel) = Platform.runLater {
        width = meterWidth * level.channels.count() + SCALE_WIDTH
        val oldChannelCount = levels?.channels?.count()
        this.levels = level
        if (level.channels.count() != oldChannelCount) {
            graphicsContext2D.clearRect(0.0, 0.0, width, height)
            paintScale()
        }
        for (ch in level.channels) {
            val rms = level.rms[ch]
            val peak = level.peak[ch]
            val x = ch * meterWidth
            graphicsContext2D.fill = Color.gray(0.08)
            graphicsContext2D.fillRect(x, VERTICAL_PADDING, meterWidth - 1, height - VERTICAL_PADDING * 2)

            val color = when {
                rms < -60.0 || rms.isNaN() -> continue
                rms > 0.0 -> Color.RED
                rms > -6.0 -> Color.YELLOW
                else -> Color.GREEN
            }
            graphicsContext2D.fill = color
            val rmsY = levelToY(rms)
            val peakY = levelToY(peak)
            graphicsContext2D.fillRect(x, rmsY, meterWidth - 1, height - rmsY - VERTICAL_PADDING)
            if (peakY < rmsY) {
                graphicsContext2D.fill = color.darker()
                graphicsContext2D.fillRect(x, peakY, meterWidth - 1, rmsY - peakY)
            }
        }
    }

    private fun paintScale() {
        val xOffset: Double = (levels?.channels?.count() ?: 0) * meterWidth
        graphicsContext2D.clearRect(xOffset, 0.0, width - xOffset, height)
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
        const val SCALE_WIDTH = TICK_WIDTH + NUMBER_WIDTH
        const val MIN_LEVEL = -60
        const val MAX_LEVEL = 24
        const val LEVEL_RANGE = MAX_LEVEL - MIN_LEVEL
        private const val VERTICAL_PADDING = 7.0
        private val stepPossibilities = listOf(3, 6, 12, 24)
    }
}