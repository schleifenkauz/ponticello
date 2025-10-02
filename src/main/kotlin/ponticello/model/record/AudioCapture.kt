package ponticello.model.record

interface AudioCapture {
    val status: Status

    fun prepare(dest: MultiChannelAudioBuffer)

    fun start()

    fun stop()

    fun close()

    enum class Status {
        UNPREPARED, PREPARED, RUNNING, CLOSED;
    }
}