package ponticello.ui.record

import fxutils.actions.button
import javafx.scene.layout.Pane
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import ponticello.impl.DecimalRange
import ponticello.impl.duration
import ponticello.impl.rangeTo
import ponticello.impl.zero
import ponticello.model.server.BusRegistry

open class BufferRegion(
    protected val parent: LiveAudioBufferView,
    range: DecimalRange = zero..zero
) : Pane() {
    var bufferRange = range
        set(value) {
            field = value
            rescale()
        }

    private val playBtn = MaterialDesignP.PLAY.button("Play region", style = "buffer-play-button") { ev ->
        play()
        ev.consume()
    }

    private fun play() {
        val out = parent.context[BusRegistry].getDefault()
        parent.buffer.playBuffer(bufferRange, out, parent.bufferObject.format, parent.context)
    }

    fun clear() {
        bufferRange = zero..zero
    }

    init {
        styleClass.add("buffer-region")
        children.add(playBtn)
        playBtn.layoutXProperty().bind(widthProperty().subtract(playBtn.widthProperty().add(5)))
        playBtn.layoutY = 5.0
        playBtn.visibleProperty().bind(
            widthProperty().greaterThanOrEqualTo(playBtn.widthProperty().add(10)).and(
                heightProperty().greaterThanOrEqualTo(playBtn.heightProperty().add(10))
            )
        )
        rescale()
        prefHeightProperty().bind(parent.heightProperty())
    }

    fun rescale() {
        if (bufferRange.isEmpty()) {
            isVisible = false
        } else {
            isVisible = true
            layoutX = parent.getX(bufferRange.start)
            prefWidth = parent.getWidth(bufferRange.duration)
        }
    }
}