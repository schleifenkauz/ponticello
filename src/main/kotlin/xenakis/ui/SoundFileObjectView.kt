package xenakis.ui

import bundles.createBundle
import javafx.scene.Cursor
import javafx.scene.input.MouseEvent
import javafx.scene.shape.Line
import javafx.scene.shape.Polyline
import xenakis.impl.UDPSuperColliderClient
import xenakis.impl.readChannels
import xenakis.model.SoundFileObject
import xenakis.sc.view.BusSelectorControl
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

class SoundFileObjectView(val obj: SoundFileObject) : ScoreObjectView(obj) {
    private var stream: AudioInputStream = AudioSystem.getAudioInputStream(obj.file)
    private val frameRate get() = stream.format.frameRate
    private val fileDuration get() = (stream.frameLength / frameRate).toDouble()
    private var channels = stream.readChannels()
    private val waveForms = Array(channels.size) { Polyline().styleClass("waveform-line") }
    private val separatorLines = Array(channels.size) { Line().styleClass("channel-separator-line") }
    private val outBusSelector = BusSelectorControl(obj.outBus, createBundle())

    override val supportedActions: List<Icon>
        get() = super.supportedActions - Icon.ExtraWindow

    init {
        envelopesPane.children.addAll(*waveForms, *separatorLines)
        waveForms.forEach { l -> l.toBack() }
        separatorLines.forEach { l -> l.toBack() }
    }

    private fun displayWaveForm() {
        val heightPerChannel = envelopesPane.height / channels.size
        for (ch in channels.indices) {
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
                val value = channels[ch][sampleIndex.coerceIn(channels[ch].indices)]
                val y = baseY + heightPerChannel * (-value / 2 + 1)
                waveForms[ch].points.addAll(x.toDouble(), y)
            }
        }
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        var newDuration = pane.getDuration(width)
        if (ev.isAltDown) {
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
        addAction(Icon.FileReload, "Reload sound file from disk") {
            obj.reloadFile(context[UDPSuperColliderClient])
            val stream = AudioSystem.getAudioInputStream(obj.file)
            channels = stream.readChannels()
            obj.duration = fileDuration / obj.rate
            rescale()
        }
    }

    override fun rescale() {
        super.rescale()
        displayWaveForm()
    }
}
