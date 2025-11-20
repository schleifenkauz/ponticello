package ponticello.ui.record

import javafx.scene.input.MouseButton
import javafx.scene.shape.Line
import ponticello.impl.Decimal

class BufferSeparator(
    private val parent: LiveAudioBufferView,
    val position: Decimal
) : Line() {
    init {
        styleClass.add("buffer-separator")
        endYProperty().bind(parent.heightProperty())
        reposition()
        setOnMouseClicked { ev ->
            if (ev.button == MouseButton.SECONDARY) {
                parent.buffer.removeSeparator(position)
                ev.consume()
            }
        }
    }

    fun reposition() {
        startX = parent.getX(position)
        endX = startX
    }
}