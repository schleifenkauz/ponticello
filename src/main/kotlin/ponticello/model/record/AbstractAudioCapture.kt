package ponticello.model.record

abstract class AbstractAudioCapture : AudioCapture {
    protected lateinit var buffer: MultiChannelAudioBuffer
        private set
    lateinit var channelConfig: ChannelConfiguration
        private set
    final override var status: AudioCapture.Status = AudioCapture.Status.UNPREPARED
        private set

    protected abstract fun doPrepare(): Boolean

    protected abstract fun doStart(): Boolean

    protected abstract fun doStop()

    protected abstract fun doClose()

    final override fun prepare(dest: MultiChannelAudioBuffer, config: ChannelConfiguration) {
        check(status == AudioCapture.Status.UNPREPARED) { "Illegal status: $status" }
        require(dest.nChannels == config.outputChannels) {
            "Invalid number of destination channels: ${dest.channels}. Expected: ${config.outputChannels}."
        }
        this.channelConfig = config
        buffer = dest
        doPrepare()
        status = AudioCapture.Status.PREPARED
    }

    final override fun start() {
        check(status == AudioCapture.Status.PREPARED) { "Illegal status: $status" }
        doStart()
        status = AudioCapture.Status.RUNNING
    }

    final override fun stop() {
        check(status == AudioCapture.Status.RUNNING) { "Illegal status: $status" }
        doStop()
        status = AudioCapture.Status.PREPARED
    }

    final override fun close() {
        check(status in setOf(AudioCapture.Status.PREPARED, AudioCapture.Status.RUNNING)) { "Illegal status: $status" }
        doClose()
        status = AudioCapture.Status.CLOSED
    }
}