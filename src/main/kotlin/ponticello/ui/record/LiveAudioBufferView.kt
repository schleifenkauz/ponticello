package ponticello.ui.record

import fxutils.actions.registerShortcuts
import fxutils.registerShortcuts
import fxutils.styleClass
import fxutils.undo.AbstractEdit
import fxutils.undo.UndoManager
import javafx.animation.Interpolator
import javafx.animation.Transition
import javafx.application.Platform
import javafx.event.Event
import javafx.scene.input.MouseButton
import javafx.scene.layout.Pane
import javafx.scene.robot.Robot
import javafx.scene.shape.Line
import javafx.util.Duration
import ponticello.impl.*
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.model.record.LiveBufferObject
import ponticello.model.record.MultiChannelAudioBuffer
import ponticello.model.server.BufferRegistry
import ponticello.model.server.BusRegistry
import ponticello.model.server.SampleObject
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.actions.UndoRedoActions
import ponticello.ui.controls.NamePrompt
import ponticello.ui.score.ScoreObjectDuplicator
import reaktive.collection.binding.size
import reaktive.list.reactiveList
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import java.nio.FloatBuffer

class LiveAudioBufferView(
    val bufferObject: LiveBufferObject,
    initialDisplayRange: DecimalRange
) : Pane(), MultiChannelAudioBuffer.Listener {
    val buffer get() = bufferObject.buffer

    val context get() = bufferObject.context
    private val undoManager = UndoManager.newInstance()

    private var displayRange = initialDisplayRange
    private val canvases = mutableListOf<LiveAudioBufferCanvas>()

    private val pixelsPerSecond get() = width / displayRange.duration.toDouble()
    private val separators = mutableListOf<BufferSeparator>()
    private val recordCursor = Line() styleClass "record-cursor"
    private val playbackCursors = reactiveList<PlaybackCursor>()

    val cursorCount = playbackCursors.size

    private val selectedRegionRect = SelectedBufferRegion(this)
    private val hoveredRegionRect = BufferRegion(this) styleClass "hovered-buffer-region"
    val selectedRange get() = selectedRegionRect.bufferRange
    private val associatedSampleObjects = mutableMapOf<DecimalRange, SampleObject>()

    init {
        val channelHeight = heightProperty().divide(buffer.nChannels)
        for ((ch, buf) in buffer.channels.withIndex()) {
            val canvas = bufferObject.viewConfig.createBufferCanvas(buf, displayRange)
            canvases.add(canvas)
            canvas.widthProperty().bind(widthProperty())
            canvas.heightProperty().bind(channelHeight)
            canvas.layoutYProperty().bind(channelHeight.multiply(ch))
        }
        children.addAll(canvases)
        children.add(selectedRegionRect)
        children.add(hoveredRegionRect)
        children.add(recordCursor)
        recordCursor.endYProperty().bind(heightProperty())
        recordCursor.endXProperty().bind(recordCursor.startXProperty())
        recordCursor.visibleProperty().bind(bufferObject.enabled.asObservableValue())
        buffer.addListener(this)
        setupInteraction()
        setupScrollingAndZooming()
        registerShortcuts(UndoRedoActions.withContext(undoManager))
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
                    if (range != null && ev.isShiftDown && !selectedRange.isEmpty() && range != selectedRange) {
                        val start = minOf(selectedRange.start, range.start)
                        val end = maxOf(selectedRange.endInclusive, range.endInclusive)
                        selectRegion(start..end)
                    } else {
                        selectRegion(range)
                    }
                }

                MouseButton.SECONDARY -> {
                    val t = getTime(ev.x)
                    buffer.addSeparator(t)
                    undoManager.record(AddSeparator(buffer, t))
                    hoveredRegionRect.clear()
                    selectRegion(null)
                }

                else -> return@setOnMouseClicked
            }
            ev.consume()
        }
        setOnMouseMoved { ev ->
            mouseMoved(ev.x)
            ev.consume()
        }
        registerShortcuts {
            on("Ctrl+J") {
                val removedSeparators = mutableListOf<Decimal>()
                for (sep in separators.toList()) {
                    if (sep.position > selectedRange.start && sep.position < selectedRange.endInclusive) {
                        buffer.removeSeparator(sep.position)
                        removedSeparators.add(sep.position)
                    }
                }
                hoveredRegionRect.clear()
                undoManager.record(JoinBufferRegions(buffer, removedSeparators))
            }
        }
        setOnMouseExited { hoveredRegionRect.clear() }
    }

    private fun mouseMoved(x: Double) {
        val t = getTime(x)
        val range = buffer.getSnippet(t)
        if (range == null || (range.start in selectedRange && range.endInclusive in selectedRange)) {
            hoveredRegionRect.clear()
        } else hoveredRegionRect.bufferRange = range
    }

    private fun getTime(x: Double) = displayRange.start + (x / pixelsPerSecond).toDecimal()
    fun getX(time: Decimal) = ((time - displayRange.start).toDouble() * pixelsPerSecond)
    fun getWidth(duration: Decimal) = duration.toDouble() * pixelsPerSecond

    private fun display(range: DecimalRange) {
        displayRange = if (range.start < zero) displayRange - range.start else range
        for (canvas in canvases) {
            canvas.display(displayRange)
        }
        for (sep in separators) {
            sep.reposition()
        }
        recordCursor.startX = getX(buffer.duration)
        selectedRegionRect.rescale()
        val p = screenToLocal(Robot().mousePosition)
        mouseMoved(p.x)
    }

    override fun addedSeparator(position: Decimal) = Platform.runLater {
        val sep = BufferSeparator(this, position)
        var idx = separators.binarySearchBy(position, selector = BufferSeparator::position)
        if (idx >= 0) return@runLater
        idx = -(idx + 1)
        separators.add(idx, sep)
        children.add(sep)
    }

    override fun removedSeparator(position: Decimal) = Platform.runLater {
        val sep = separators.find { it.position == position } ?: return@runLater
        separators.remove(sep)
        children.remove(sep)
    }

    override fun accept(sampleOffset: Long, samples: List<FloatBuffer>, frames: Int) {
        for ((buf, canvas) in samples.zip(canvases)) {
            canvas.accept(sampleOffset, buf.position(0), frames)
        }
        val position = buffer.duration
        Platform.runLater {
            recordCursor.startX = getX(position)
        }
    }

    private fun selectRegion(range: DecimalRange?) {
        if (range == null) selectedRegionRect.clear()
        else {
            selectedRegionRect.bufferRange = range
            hoveredRegionRect.clear()
            selectedRegionRect.requestFocus()
        }
    }

    fun createSoundProcess(range: DecimalRange, ev: Event?) {
        val sample = associatedSampleObjects.getOrPut(range) {
            val name = NamePrompt(context[BufferRegistry], "Sample name", "").showDialog(ev) ?: return
            val samplesDir = context.project.projectDirectory.resolve("samples")
            samplesDir.mkdirs()
            val sampleFile = samplesDir.resolve("$name.wav")
            buffer.writeTo(sampleFile, bufferObject.format, range)
            val sample = SampleObject.create(name, sampleFile)
            context[BufferRegistry].add(sample)
            sample
        }
        context[ScoreObjectDuplicator].enterDuplicateMode(sample, ev)
    }

    private fun zoom(amount: Double, evX: Double) {
        val newIntervalSize = (displayRange.duration) * amount
        val oldIntervalCenter = (displayRange.endInclusive + displayRange.start) / 2
        val newIntervalCenter = (getTime(evX) + oldIntervalCenter * 3) / 4
        val newStart = newIntervalCenter - (newIntervalSize / 2)
        val newEnd = newIntervalCenter + (newIntervalSize / 2)
        display(newStart..newEnd)
    }

    private fun scroll(amount: Double) {
        display(displayRange + amount.toDecimal())
    }

    override fun cleared() {
        children.removeAll(separators)
        separators.clear()
        selectedRegionRect.bufferRange = zero..zero
        for (canvas in canvases) {
            canvas.clear()
        }
        recordCursor.startX = 0.0
        displayRange -= displayRange.start
    }

    fun playBufferRange(range: DecimalRange) {
        val out = context[BusRegistry].getDefault()
        val synthName = buffer.playBuffer(range, out, bufferObject.format, context)
        val cursor = PlaybackCursor(this, range, synthName)
        playbackCursors.now.add(cursor)

        cursor.playFromStart()
    }

    fun stopPlayback() {
        for (cursor in playbackCursors.now.toList()) {
            cursor.stop()
        }
    }

    private class PlaybackCursor(
        private val parent: LiveAudioBufferView,
        private val range: DecimalRange,
        private val synthName: String
    ) : Transition() {
        private val line = Line() styleClass "play-head"

        init {
            line.endYProperty().bind(parent.heightProperty())
            line.endXProperty().bind(line.startXProperty())
            cycleDuration = Duration.seconds(range.duration.toDouble())
            delay = Duration.seconds(parent.context.project[PLAYBACK_SETTINGS].serverLatency.now.toDouble())
            interpolator = Interpolator.LINEAR
            setOnFinished { finished() }
        }

        private fun finished() {
            parent.children.remove(line)
            parent.playbackCursors.now.remove(this)
        }

        override fun interpolate(frac: Double) {
            line.startX = parent.getX(range.start + frac * range.duration)
        }

        override fun playFromStart() {
            parent.children.add(line)
            super.play()
        }

        override fun stop() {
            super.stop()
            finished()
            parent.context[SuperColliderClient].run("$synthName.stop; $synthName.close")
        }
    }

    private class AddSeparator(
        private val buffer: MultiChannelAudioBuffer,
        private val position: Decimal
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Add buffer separator"

        override fun doRedo() {
            buffer.addSeparator(position)
        }

        override fun doUndo() {
            buffer.removeSeparator(position)
        }
    }

    private class JoinBufferRegions(
        private val buffer: MultiChannelAudioBuffer,
        private val separators: List<Decimal>
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Join buffer regions"

        override fun doUndo() {
            for (pos in separators) {
                buffer.addSeparator(pos)
            }
        }

        override fun doRedo() {
            for (pos in separators) {
                buffer.removeSeparator(pos)
            }
        }
    }
}