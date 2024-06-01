package xenakis.ui

import bundles.createBundle
import javafx.scene.Cursor
import javafx.scene.input.MouseEvent
import javafx.scene.shape.Line
import javafx.scene.shape.Polyline
import reaktive.Observer
import xenakis.impl.readChannels
import xenakis.model.PlayBufObject
import xenakis.sc.view.ObjectSelectorControl
import javax.sound.sampled.AudioFormat

class PlayBufObjectView(val obj: PlayBufObject) : ScoreObjectView(obj) {
    private lateinit var format: AudioFormat
    private lateinit var contents: Array<DoubleArray>
    private var waveForms = emptyArray<Polyline>()
    private var separatorLines = emptyArray<Line>()

    private val outBusSelector = ObjectSelectorControl(obj.outSelector, createBundle())

    private val contentsObserver: Observer

    override val supportedActions: List<Icon>
        get() = super.supportedActions - Icon.ExtraWindow

    init {
        envelopesPane.children.addAll(*waveForms, *separatorLines)
        contentsObserver = obj.buffer.get().contentsChanged.observe { _, _ -> updateContentDisplay() }
        updateContentDisplay()
    }

    private fun updateContentDisplay() {
        obj.buffer.get().useAudioStream { stream ->
            if (stream != null) {
                format = stream.format
                contents = stream.readChannels()
                displayWaveForm()
            } else {
                envelopesPane.children.removeAll(*waveForms, *separatorLines)
            }
        }
    }

    private fun displayWaveForm() {
        envelopesPane.children.removeAll(*waveForms, *separatorLines)
        waveForms = Array(contents.size) { Polyline().styleClass("waveform-line") }
        separatorLines = Array(contents.size) { Line().styleClass("channel-separator-line") }
        val heightPerChannel = envelopesPane.height / contents.size
        for (ch in contents.indices) {
            val baseY = ch * heightPerChannel
            if (ch != 0) {
                val sep = separatorLines[ch]
                sep.endX = envelopesPane.width
                sep.startY = baseY
                sep.endY = baseY
            }
            val frameRate = format.frameRate
            for (x in 0 until envelopesPane.width.toInt()) {
                val t = obj.startPos + x / pane.pixelsPerSecond
                val sampleIndex = (t * obj.rate * frameRate).toInt()
                val value = contents[ch][sampleIndex % contents[ch].size]
                val y = baseY + heightPerChannel * (-value / 2 + 1)
                waveForms[ch].points.addAll(x.toDouble(), y)
            }
        }
        envelopesPane.children.addAll(*waveForms, *separatorLines)
        waveForms.forEach { l -> l.toBack() }
        separatorLines.forEach { l -> l.toBack() }
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        var newDuration = pane.getDuration(width)
        if (ev.isShiftDown) {
            obj.rate *= obj.duration / newDuration
        } else {
            newDuration = pane.getDuration(width)
            if (cursor in setOf(Cursor.W_RESIZE, Cursor.SW_RESIZE, Cursor.NW_RESIZE)) {
                newDuration = newDuration.coerceAtMost(obj.duration + obj.startPos)
                val deltaStart = obj.duration - newDuration
                obj.startPos = (obj.startPos + deltaStart * obj.rate)
            }
        }
        super.resizeObject(width, height, ev, cursor)
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        displayWaveForm()
        header.children.add(1, outBusSelector)
    }

    override fun rescale() {
        super.rescale()
        displayWaveForm()
    }
}
