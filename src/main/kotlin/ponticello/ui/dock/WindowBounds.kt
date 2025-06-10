package ponticello.ui.dock

import javafx.stage.Window
import kotlinx.serialization.Serializable

@Serializable
data class WindowBounds(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun applyTo(window: Window) {
        window.x = x
        window.y = y
        window.width = width
        window.height = height
    }
}