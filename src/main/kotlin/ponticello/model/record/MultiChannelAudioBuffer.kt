package ponticello.model.record

import hextant.context.Context
import ponticello.impl.*
import ponticello.model.obj.project
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import java.io.File
import javax.sound.sampled.AudioFormat

abstract class MultiChannelAudioBuffer(val sampleRate: Double, val nChannels: Int) {
    private var receivedFrames = 0L
    val duration get() = (receivedFrames / sampleRate).toDecimal()
    private val listeners = mutableListOf<Listener>()
    private val channelListeners = Array<MutableList<AudioBuffer.Listener>>(nChannels) { mutableListOf() }

    private val separators = mutableListOf<Decimal>()

    abstract val channels: List<AudioBuffer>

    open fun receive(samples: List<FloatArray>, frames: Int) {
        val frameOffset = receivedFrames
        receivedFrames += frames
        for ((idx, ch) in channels.withIndex()) {
            ch.append(samples[idx], frames)
        }
        for (listener in listeners) {
            listener.accept(frameOffset, samples, frames)
        }
        for ((arr, listeners) in samples.zip(channelListeners)) {
            for (listener in listeners) {
                listener.accept(frameOffset, arr, frames)
            }
        }
    }

    fun clear() {
        receivedFrames = 0
        separators.clear()
        for (ch in channels) {
            ch.clear()
        }
        for (listener in listeners) {
            listener.cleared()
        }
    }

    fun getChannel(channel: Int): AudioBuffer = channels[channel]

    fun addSeparator(position: Decimal) {
        var idx = separators.binarySearch(position)
        if (idx >= 0) return
        idx = -(idx + 1)
        separators.add(idx, position)
        listeners.forEach { it.addedSeparator(position) }
    }

    fun addSeparatorAtEnd() {
        addSeparator(duration)
    }

    fun removeSeparator(position: Decimal) {
        separators.remove(position)
        for (listener in listeners) {
            listener.removedSeparator(position)
        }
    }

    fun getSnippet(position: Decimal): DecimalRange {
        var idx = separators.binarySearch(position)
        if (idx < 0) idx = -(idx + 1)
        val start = if (idx == 0) zero else separators[idx - 1]
        val end = if (idx == separators.size) duration else separators[idx]
        return DecimalRange(start, end)
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        for (separator in separators) {
            listener.addedSeparator(separator)
        }
    }

    fun addChannelListener(idx: Int, listener: AudioBuffer.Listener) {
        channelListeners[idx].add(listener)
    }

    abstract fun writeTo(file: File, format: AudioFormat, range: DecimalRange)

    open fun loadBuffer(
        range: DecimalRange, format: AudioFormat, context: Context,
        action: ScWriter.(bufName: String) -> Unit
    ) {
        val tmpDir = context.project.projectDirectory.resolve("tmp")
        tmpDir.mkdirs()
        val tmpFile = tmpDir.resolve("tmp.wav")
        writeTo(tmpFile, format, range)
        context[SuperColliderClient].run {
            appendBlock("Buffer.read(s, ${tmpFile.superColliderPath}, action: ", endLine = false) {
                +"arg b"
                action("b")
            }
            appendLine(");")
        }
    }

    interface Listener {
        fun accept(sampleOffset: Long, samples: List<FloatArray>, frames: Int)

        fun addedSeparator(position: Decimal)

        fun removedSeparator(position: Decimal)

        fun cleared()
    }
}