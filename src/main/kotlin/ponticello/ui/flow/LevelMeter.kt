package ponticello.ui.flow

import javafx.application.Platform
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.scene.canvas.Canvas
import javafx.scene.paint.Color

class LevelMeter(height: ReadOnlyDoubleProperty) : Canvas() {
    private var level = Double.NEGATIVE_INFINITY

    init {
        width = 8.0
        heightProperty().bind(height)
        height.addListener { setLevel(level) }
        graphicsContext2D.fill = Color.GREEN
    }

    fun setLevel(lvl: Double) = Platform.runLater {
        level = lvl
        graphicsContext2D.fill = Color.gray(0.08)
        graphicsContext2D.fillRect(0.0, 0.0, width, height)
        graphicsContext2D.fill = when {
            lvl == Double.NEGATIVE_INFINITY -> return@runLater
            lvl > 0.0 -> Color.RED
            lvl > -6.0 -> Color.YELLOW
            else -> Color.GREEN
        }
        val y = (1.0 - ((lvl + 60) / 84)) * height
        graphicsContext2D.fillRect(0.0, y, width, height - y)
    }
}