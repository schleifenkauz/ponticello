package ponticello.ui.misc

import fxutils.styleClass
import javafx.scene.control.Label
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import ponticello.impl.round
import ponticello.sc.Warp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToInt

class PatternPlot() : Pane() {
    private var values: List<Double> = emptyList()
    val valueLabel: Label = Label()

    var warp: Warp = Warp.Linear
        set(value) {
            field = value
            repaint()
        }

    init {
        styleClass("pattern-plot")
        repaint()
        widthProperty().addListener { _, _, _ -> repaint() }
        heightProperty().addListener { _, _, _ -> repaint() }
    }

    fun update(values: List<Double>) {
        this.values = values
        repaint()
    }

    fun repaint() {
        children.clear()
        if (values.isEmpty()) return
        val pixelsPerIdx = width / values.size
        val min = floor(values.min())
        val max = ceil(values.max())
        val range = max - min
        val accuracy = log10(1000 / range).roundToInt()
        for ((idx, value) in values.withIndex()) {
            val x = idx * pixelsPerIdx
            val y = (value - min) / range * height
            val rect = Rectangle(x, height - y, pixelsPerIdx * 0.95, y)
            rect.fill = Color.GREEN
            val label = Text(x, y, value.toString())
            if (pixelsPerIdx >= label.prefWidth(-1.0)) {
                children.add(label)
            } else {
                rect.hoverProperty().addListener { _, _, isHovered ->
                    if (isHovered) valueLabel.text = "Value: ${value.round(accuracy)}"
                    else valueLabel.text = ""
                }
            }
            children.add(rect)
        }
    }
}