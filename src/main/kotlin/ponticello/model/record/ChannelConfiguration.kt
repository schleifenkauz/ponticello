package ponticello.model.record

import kotlinx.serialization.Serializable

@Serializable
data class ChannelConfiguration(
    val inputChannels: Int,
    val outputChannels: Int,
    val mapping: List<Int>
) {
    init {
        require(mapping.size == inputChannels) { "Invalid mapping size: ${mapping.size}" }
        require(mapping.all { ch -> ch in 0 until outputChannels })
    }

    companion object {
        fun mono() = ChannelConfiguration(1, 1, listOf(0))

        fun stereo() = ChannelConfiguration(2, 2, listOf(0, 1))

        fun default(channels: Int) = ChannelConfiguration(channels, channels, List(channels) { ch -> ch })

        fun monoMixdown(inputChannels: Int) = ChannelConfiguration(inputChannels, 1, List(inputChannels) { 0 })
    }
}