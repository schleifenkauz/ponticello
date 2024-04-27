package xenakis.sc

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ColorSerializer
import xenakis.impl.FileSerializer
import java.io.File

@Serializable
data class Group(
    var name: String,
    @Serializable(with = ColorSerializer::class) var associatedColor: Color,
) {
    val code = "~grp_$name"

    companion object {
        val default = Group("default", Color.BLUE)
    }
}

enum class Rate {
    Audio, Control;

    override fun toString(): String = when (this) {
        Audio -> "ar"
        Control -> "kr"
    }
}

@Serializable
data class Bus(
    var name: String,
    var rate: Rate,
    var channels: Int,
    @Serializable(with = ColorSerializer::class) var associatedColor: Color?
) {
    fun copyFrom(obj: Bus) {
        name = obj.name
        rate = obj.rate
        channels = obj.channels
        associatedColor = obj.associatedColor
    }

    val code get() = "~bus_$name"

    companion object {
        val output = Bus("output", Rate.Audio, 2, Color.WHITE)

        val PROPERTY_NAMES = listOf("name", "rate", "channels", "associatedColor")
    }
}

@Serializable
data class Buffer(var name: String, @Serializable(with = FileSerializer::class) var referencedFile: File? = null) {
    val code get() = "~buf_$name"
}

