package ponticello.model.record

import kotlinx.serialization.Serializable

@Serializable
data class ChannelConfiguration(
    val inputChannels: Int,
    val outputChannels: Int,
    private val mapping: List<Int>
) {
    private val reverseMapping = IntArray(inputChannels) { -1 }

    init {
        require(mapping.size == outputChannels) { "Invalid mapping size: ${mapping.size}" }
        for ((output, input) in mapping.withIndex()) {
            require(input in 0 until inputChannels)
            reverseMapping[input] = output
        }
    }

    fun map(inputChannel: Int): Int = reverseMapping[inputChannel]

    companion object {
        fun mono() = ChannelConfiguration(1, 1, listOf(0))

        fun stereo() = ChannelConfiguration(2, 2, listOf(0, 1))

        fun default(channels: Int) = ChannelConfiguration(channels, channels, List(channels) { ch -> ch })
    }
}