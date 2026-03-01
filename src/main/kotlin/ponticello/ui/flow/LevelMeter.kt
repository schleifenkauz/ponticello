package ponticello.ui.flow

import javafx.application.Platform
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color

class LevelMeter(height: ReadOnlyDoubleProperty) : Canvas() {
    private var levels = emptyList<Double>()

    init {
        heightProperty().bind(height)
        height.addListener { setLevels(levels) }
        graphicsContext2D.fill = Color.GREEN
    }

    fun setLevels(levels: List<Double>) = Platform.runLater {
        this.levels = levels
        width = METER_WIDTH * levels.size
        for ((ch, lvl) in levels.withIndex()) {
            val x = ch * METER_WIDTH
            graphicsContext2D.fill = Color.gray(0.08)
            graphicsContext2D.fillRect(x, 0.0, METER_WIDTH - 1, height)

            graphicsContext2D.fill = when {
                lvl == Double.NEGATIVE_INFINITY -> return@runLater
                lvl > 0.0 -> Color.RED
                lvl > -6.0 -> Color.YELLOW
                else -> Color.GREEN
            }
            val y = (1.0 - ((lvl + 60) / 84)) * height
            graphicsContext2D.fillRect(x, y, METER_WIDTH - 1, height - y)
        }
    }

    companion object {
        private const val METER_WIDTH = 8.0
    }
}