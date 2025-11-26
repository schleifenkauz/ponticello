package ponticello.model.record

import hextant.context.Context
import ponticello.impl.*
import ponticello.model.instr.BusObject
import ponticello.model.obj.superColliderName
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import java.io.File
import java.nio.FloatBuffer
import javax.sound.sampled.AudioFormat

abstract class MultiChannelAudioBuffer(val sampleRate: Double, val nChannels: Int) {
    private var receivedFrames = 0L
    val duration get() = (receivedFrames / sampleRate).toDecimal()
    private val listeners = mutableListOf<Listener>()
    private val channelListeners = Array<MutableList<AudioBuffer.Listener>>(nChannels) { mutableListOf() }

    private val separators = mutableListOf<Decimal>()

    abstract val channels: List<AudioBuffer>

    protected open fun write(samples: List<FloatBuffer>, frames: Int) {}

    fun receive(samples: List<FloatBuffer>, frames: Int) {
        write(samples, frames)
        val frameOffset = receivedFrames
        receivedFrames += frames
        for ((idx, ch) in channels.withIndex()) {
            ch.append(samples[idx].position(0), frames)
        }
        for (listener in listeners) {
            listener.accept(frameOffset, samples, frames)
        }
        for ((buf, listeners) in samples.zip(channelListeners)) {
            for (listener in listeners) {
                listener.accept(frameOffset, buf.position(0), frames)
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

    fun getSnippet(position: Decimal): DecimalRange? {
        var idx = separators.binarySearch(position)
        if (idx < 0) idx = -(idx + 1)
        if (idx == separators.size) return null
        val start = if (idx == 0) zero else separators[idx - 1]
        val end = separators[idx]
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

    protected fun loadBuffer(
        audioFile: File, frameOffset: Long, numFrames: Long, context: Context,
        action: ScWriter.(bufName: String) -> Unit
    ) {
        val path = audioFile.superColliderPath
        context[SuperColliderClient].run {
            +"var path = $path"
            appendBlock("Buffer.read(s, path, $frameOffset, $numFrames, action: ", endLine = false) {
                +"arg b"
                action("b")
                +"File.delete(path)"
            }
            appendLine(");")
        }
    }

    protected fun playBuffer(audioFile: File, range: DecimalRange, outBus: BusObject, context: Context): String {
        val path = audioFile.superColliderPath
        val frameOffset = (range.start * sampleRate).toLong()
        val synthName = "~play_buf_${playbackSynthsCounter++}"
        context[SuperColliderClient].run {
            +"var buf"
            +"buf = Buffer.cueSoundFile(s, $path, $frameOffset, $nChannels)"
            appendBlock("$synthName = ", endLine = false) {
                +"var snd = DiskIn.ar($nChannels, buf), env"
                +"env = Env.linen(0.01, ${range.duration - 0.02}, 0.01)"
                +"snd * env.kr(Done.freeSelf)"
            }
            +".play(s, ${outBus.superColliderName}).onFree { buf.free }"
        }
        return synthName
    }

    abstract fun writeTo(file: File, format: AudioFormat, range: DecimalRange)

    abstract fun loadBuffer(
        range: DecimalRange, format: AudioFormat, context: Context, action: ScWriter.(bufName: String) -> Unit
    )

    abstract fun playBuffer(range: DecimalRange, outBus: BusObject, format: AudioFormat, context: Context): String

    interface Listener {
        fun accept(sampleOffset: Long, samples: List<FloatBuffer>, frames: Int)

        fun addedSeparator(position: Decimal)

        fun removedSeparator(position: Decimal)

        fun cleared()
    }

    companion object {
        private var playbackSynthsCounter = 0
    }
}