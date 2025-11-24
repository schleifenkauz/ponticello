package ponticello.ui.record

import fxutils.styleClass
import javafx.application.Platform
import javafx.scene.input.MouseButton
import javafx.scene.layout.Pane
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import ponticello.impl.*
import ponticello.model.record.LiveBufferObject
import ponticello.model.record.MultiChannelAudioBuffer

class LiveAudioBufferView(
    private val obj: LiveBufferObject,
    initialDisplayRange: DecimalRange
) : Pane(), MultiChannelAudioBuffer.Listener {
    val buffer get() = obj.buffer

    private var displayRange = initialDisplayRange
    private val canvases = mutableListOf<LiveAudioBufferCanvas>()

    private val pixelsPerSecond get() = width / displayRange.dur.toDouble()
    private val separators = mutableListOf<BufferSeparator>()
    private val recordCursor = Line() styleClass "record-cursor"

    private val selectedRegionRect = Rectangle() styleClass "buffer-selection"

    var selectedRange: DecimalRange? = null
        private set(value) {
            field = value
            displaySelectedRegion()
        }

    init {
        val channelHeight = heightProperty().divide(buffer.nChannels)
        for ((ch, buf) in buffer.channels.withIndex()) {
            val canvas = obj.viewConfig.createBufferCanvas(buf, displayRange)
            canvases.add(canvas)
            canvas.widthProperty().bind(widthProperty())
            canvas.heightProperty().bind(channelHeight)
            canvas.layoutYProperty().bind(channelHeight.multiply(ch))
        }
        children.addAll(canvases)
        children.add(selectedRegionRect)
        selectedRegionRect.heightProperty().bind(heightProperty())
        selectedRegionRect.isVisible = false
        children.add(recordCursor)
        recordCursor.endYProperty().bind(heightProperty())
        buffer.addListener(this)
        setupInteraction()
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

    private fun setupInteraction() {
        setOnMouseClicked { ev ->
            when (ev.button) {
                MouseButton.PRIMARY -> {
                    val t = getTime(ev.x)
                    val range = buffer.getSnippet(t)
                    selectRegion(range)
                }

                MouseButton.SECONDARY -> {
                    val t = getTime(ev.x)
                    buffer.addSeparator(t)
                }

                else -> {}
            }
        }
    }

    private fun getTime(x: Double) = displayRange.start + (x / pixelsPerSecond).toDecimal()

    fun getX(time: Decimal) = ((time - displayRange.start).toDouble() * pixelsPerSecond)

    private fun display(range: DecimalRange) {
        displayRange = if (range.start < zero) displayRange - range.start else range
        for (canvas in canvases) {
            canvas.display(displayRange)
        }
        for (sep in separators) {
            sep.reposition()
        }
        recordCursor.startX = getX(buffer.duration)
        recordCursor.endX = recordCursor.startX
        displaySelectedRegion()
    }

    override fun addedSeparator(position: Decimal) {
        Platform.runLater {
            val sep = BufferSeparator(this, position)
            children.add(sep)
            separators.add(sep)
        }
    }

    private fun displaySelectedRegion() {
        val range = selectedRange
        if (range == null) {
            selectedRegionRect.isVisible = false
        } else {
            selectedRegionRect.isVisible = true
            selectedRegionRect.width = (range.dur * pixelsPerSecond).value
            selectedRegionRect.x = getX(range.start)
        }
    }

    override fun accept(sampleOffset: Long, samples: List<FloatArray>, frames: Int) {
        for ((arr, canvas) in samples.zip(canvases)) {
            canvas.accept(sampleOffset, arr, frames)
        }
        val position = buffer.duration
        Platform.runLater {
            recordCursor.startX = getX(position)
            recordCursor.endX = recordCursor.startX
        }
    }

    private fun selectRegion(range: DecimalRange) {
        selectedRange = range
        displaySelectedRegion()
        selectedRegionRect.requestFocus()
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

    override fun removedSeparator(position: Decimal) {
        val sep = separators.find { it.position == position } ?: return
        separators.remove(sep)
        children.remove(sep)
    }

    override fun cleared() {
        children.removeAll(separators)
        separators.clear()
        for (canvas in canvases) {
            canvas.clear()
        }
    }
}