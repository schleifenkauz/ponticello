package xenakis.model.project

import javafx.geometry.Dimension2D
import javafx.stage.Stage
import javafx.stage.Window
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class WindowState {
    private var x: Double? = null
    private var y: Double? = null
    private var width: Double? = null
    private var height: Double? = null

    @Transient
    private var target: Window? = null

    open fun applyTo(window: Stage, defaultSize: Dimension2D?) {
        target = window
        x?.let(window::setX)
        y?.let(window::setY)
        if (width == null && height == null && defaultSize != null) {
            window.width = defaultSize.width
            window.height = defaultSize.height
        } else {
            width?.let(window::setWidth)
            height?.let(window::setHeight)
        }
    }

    open fun saveFromTarget() {
        val target = target ?: return
        x = target.x.takeIf { it.isFinite() }
        y = target.y.takeIf { it.isFinite() }
        width = target.width.takeIf { it.isFinite() }
        height = target.height.takeIf { it.isFinite() }
    }
}