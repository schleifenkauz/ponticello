package ponticello.ui.live

import javafx.scene.canvas.Canvas
import javafx.scene.layout.StackPane

class SpectrogramCanvas(displayRange: DoubleRange) : StackPane() {
    private val canvas = Canvas()

    var displayRange: DoubleRange = displayRange
        set(value) {
            field = value
        }
}