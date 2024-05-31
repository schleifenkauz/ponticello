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
import javax.sound.sampled.AudioInputStream

class SoundFileObjectView(val obj: PlayBufObject) : ScoreObjectView(obj) {
    private lateinit var stream: AudioInputStream
    private val frameRate get() = stream.format.frameRate
    private val fileDuration get() = (stream.frameLength / frameRate).toDouble()
    private lateinit var contents: Array<DoubleArray>
    private val waveForms = Array(contents.size) { Polyline().styleClass("waveform-line") }
    private val separatorLines = Array(contents.size) { Line().styleClass("channel-separator-line") }
    private val outBusSelector = ObjectSelectorControl(obj.outSelector, createBundle())

    private val contentsObserver: Observer

    override val supportedActions: List<Icon>
        get() = super.supportedActions - Icon.ExtraWindow

    init {
        envelopesPane.children.addAll(*waveForms, *separatorLines)
        waveForms.forEach { l -> l.toBack() }
        separatorLines.forEach { l -> l.toBack() }
        contentsObserver = obj.buffer.get().contentsChanged.observe { _, _ -> updateContentDisplay() }
        updateContentDisplay()
    }

    private fun updateContentDisplay() {
        stream = obj.buffer.get().getAudioStream()
        contents = stream.readChannels()
        displayWaveForm()
    }

    private fun displayWaveForm() {
        val heightPerChannel = envelopesPane.height / contents.size
        for (ch in contents.indices) {
            val baseY = ch * heightPerChannel
            if (ch != 0) {
                val sep = separatorLines[ch]
                sep.endX = envelopesPane.width
                sep.startY = baseY
                sep.endY = baseY
            }
            waveForms[ch].points.clear()
            for (x in 0 until envelopesPane.width.toInt()) {
                val t = obj.startPos + x / pane.pixelsPerSecond
                val sampleIndex = (t * obj.rate * frameRate).toInt()
                val value = contents[ch][sampleIndex.coerceIn(contents[ch].indices)]
                val y = baseY + heightPerChannel * (-value / 2 + 1)
                waveForms[ch].points.addAll(x.toDouble(), y)
            }
        }
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        var newDuration = pane.getDuration(width)
        if (ev.isShiftDown) {
            obj.rate *= obj.duration / newDuration
        } else {
            newDuration = pane.getDuration(width).coerceAtMost(fileDuration / obj.rate)
            if (cursor in setOf(Cursor.W_RESIZE, Cursor.SW_RESIZE, Cursor.NW_RESIZE)) {
                val deltaStart = obj.duration - newDuration
                obj.startPos = (obj.startPos + deltaStart * obj.rate).coerceIn(0.0, fileDuration)
            }
        }
        obj.duration = newDuration
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
