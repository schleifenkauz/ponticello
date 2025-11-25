package ponticello.model.record

import reaktive.value.ReactiveValue

interface AudioCapture {
    val status: ReactiveValue<Status>

    fun prepare(dest: MultiChannelAudioBuffer, config: ChannelConfiguration): Boolean

    fun start()

    fun stop()

    fun close()

    enum class Status {
        UNPREPARED, PREPARED, RUNNING, CLOSED;
    }
}