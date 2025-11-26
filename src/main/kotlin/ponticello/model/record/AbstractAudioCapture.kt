package ponticello.model.record

import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.nio.FloatBuffer

abstract class AbstractAudioCapture : AudioCapture {
    protected lateinit var buffer: MultiChannelAudioBuffer
        private set
    lateinit var channelConfig: ChannelConfiguration
        private set
    private lateinit var threshold: LoudnessThreshold

    private var _status = reactiveVariable(AudioCapture.Status.UNPREPARED)
    override val status: ReactiveValue<AudioCapture.Status> get() = _status
    private var lastBufferPassedThreshold = false

    protected abstract fun doPrepare(): Boolean

    protected abstract fun doStart(): Boolean

    protected abstract fun doStop()

    protected abstract fun doClose()

    private fun checkStatus(vararg expected: AudioCapture.Status) {
        check(status.now in expected) { "Illegal status: $status. Expected $expected" }
    }

    final override fun prepare(
        dest: MultiChannelAudioBuffer,
        config: ChannelConfiguration,
        threshold: LoudnessThreshold
    ): Boolean {
        checkStatus(AudioCapture.Status.UNPREPARED)
        require(dest.nChannels == config.outputChannels) {
            "Invalid number of destination channels: ${dest.channels}. Expected: ${config.outputChannels}."
        }
        this.channelConfig = config
        this.threshold = threshold
        buffer = dest
        if (doPrepare()) {
            _status.now = AudioCapture.Status.PREPARED
            return true
        } else return false
    }

    final override fun start() {
        checkStatus(AudioCapture.Status.PREPARED)
        doStart()
        _status.now = AudioCapture.Status.RUNNING
    }

    protected fun process(samples: List<FloatBuffer>, frames: Int) {
        val passes = threshold.process(samples, frames)
        if (passes) {
            buffer.receive(samples, frames)
        } else if (lastBufferPassedThreshold) {
            buffer.addSeparatorAtEnd()
        }
        lastBufferPassedThreshold = passes
    }

    final override fun stop() {
        checkStatus(AudioCapture.Status.RUNNING)
        doStop()
        buffer.addSeparatorAtEnd()
        _status.now = AudioCapture.Status.PREPARED
    }

    final override fun close() {
        checkStatus(AudioCapture.Status.RUNNING, AudioCapture.Status.PREPARED)
        doClose()
        _status.now = AudioCapture.Status.CLOSED
    }

}