package ponticello.ui.record

import javafx.scene.layout.VBox
import ponticello.impl.*
import ponticello.model.record.LiveBufferObject

class LiveAudioBufferView(
    private val buffer: LiveBufferObject,
    initialDisplayRange: DecimalRange
) : VBox() {
    private var displayRange = initialDisplayRange
    private val canvases = mutableListOf<LiveAudioBufferCanvas>()

    private val pixelsPerSecond get() = width / displayRange.dur.toDouble()

    init {
        val channelHeight = heightProperty().divide(buffer.buffer.channelConfig.outputChannels)
        for ((ch, buf) in buffer.buffer.channels.withIndex()) {
            val canvas = buffer.viewConfig.createBufferCanvas(buf, displayRange)
            canvases.add(canvas)
            canvas.widthProperty().bind(widthProperty())
            canvas.heightProperty().bind(channelHeight)
//            canvas.layoutYProperty().bind(channelHeight.multiply(ch))
        }
        children.addAll(canvases)
        setupScrollingAndZooming()
    }

    private fun setupScrollingAndZooming() {
        setOnScroll { ev ->
            val delta = when {
                ev.deltaX != 0.0 -> ev.deltaX
                ev.deltaY != 0.0 -> ev.deltaY
                else -> return@setOnScroll
            }
            if (ev.isControlDown) {
                val factor = kotlin.math.exp(-delta * 0.01)
                zoom(factor, ev.x)
            } else {
                scroll(-delta / pixelsPerSecond)
            }
        }
    }


    private fun getTime(x: Double) = displayRange.start + (x / pixelsPerSecond).toDecimal()

    private fun display(range: DecimalRange) {
        for (canvas in canvases) {
            canvas.display(range)
        }
    }

    fun zoom(amount: Double, evX: Double) {
        val newIntervalSize = (displayRange.dur) * amount
        val oldIntervalCenter = (displayRange.endInclusive + displayRange.start) / 2
        val newIntervalCenter = (getTime(evX) + oldIntervalCenter * 3) / 4
        val newStart = newIntervalCenter - (newIntervalSize / 2)
        val newEnd = newIntervalCenter + (newIntervalSize / 2)
        display(newStart..newEnd)
    }

    fun scroll(amount: Double) {
        display(displayRange + amount.toDecimal())
    }
}