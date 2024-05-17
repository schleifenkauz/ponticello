package xenakis.ui

import javafx.scene.input.MouseEvent
import javafx.scene.shape.Line
import javafx.scene.shape.Polyline
import xenakis.impl.readChannels
import xenakis.model.SoundFileObject
import xenakis.model.XenakisProject
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

class SoundFileObjectView(override val obj: SoundFileObject, project: XenakisProject) : ScoreObjectView(obj, project) {
    private val stream: AudioInputStream = AudioSystem.getAudioInputStream(obj.file)
    private val frameRate = stream.format.frameRate
    private val fileDuration = (stream.frameLength / frameRate).toDouble()
    private val channels = stream.readChannels()
    private val waveForms = Array(channels.size) { Polyline().styleClass("waveform-line") }
    private val separatorLines = Array(channels.size) { Line().styleClass("channel-separator-line") }

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
                val t = obj.startPos + x / scoreView.pixelsPerSecond
                val sampleIndex = (t * obj.rate * frameRate).toInt()
                val value = channels[ch][sampleIndex.coerceIn(channels[ch].indices)]
                val y = baseY + heightPerChannel * (-value / 2 + 1)
                waveForms[ch].points.addAll(x.toDouble(), y)
            }
        }
    }

    override fun setObjectWidth(width: Double, ev: MouseEvent, resizeFromLeft: Boolean) {
        var newDuration = scoreView.getDuration(width)
        if (ev.isShiftDown) {
            obj.rate *= obj.duration / newDuration
        } else {
            newDuration = scoreView.getDuration(width).coerceAtMost(fileDuration / obj.rate)
            if (resizeFromLeft) {
                val deltaStart = obj.duration - newDuration
                obj.startPos = (obj.startPos + deltaStart * obj.rate).coerceIn(0.0, fileDuration)
            }
        }
        obj.duration = newDuration
    }

    override fun repaint() {
        super.repaint()
        displayWaveForm()
    }

    override fun rescale() {
        super.rescale()
        displayWaveForm()
    }
}
