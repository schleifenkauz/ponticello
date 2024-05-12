package xenakis.sc

import hextant.codegen.Choice
import hextant.codegen.UseEditor
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ColorSerializer
import xenakis.impl.FileSerializer
import xenakis.impl.superColliderPath
import xenakis.sc.editor.BufferRefEditor
import xenakis.sc.editor.BusRefEditor
import java.io.File

@Serializable
data class Group(
    var name: String,
    @Serializable(with = ColorSerializer::class) var associatedColor: Color,
) {
    val variableName = "~grp_$name"

    companion object {
        val default = Group("default", Color.BLUE)
    }
}

@Choice(defaultValue = "Rate.Audio")
enum class Rate {
    Audio, Control;

    override fun toString(): String = when (this) {
        Audio -> "ar"
        Control -> "kr"
    }
}

@Serializable
@UseEditor(BusRefEditor::class)
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

    val variableName get() = if (name != "output") "~bus_$name" else "0"

    val allocationCode get() = "$variableName = Bus.${rate.name.lowercase()}(s, $channels)"

    companion object {
        val output = Bus("output", Rate.Audio, 2, Color.WHITE)

        val PROPERTY_NAMES = listOf("name", "rate", "channels", "associatedColor")
    }
}

@Serializable
@UseEditor(BufferRefEditor::class)
sealed interface Buffer {
    val name: String

    val variableName get() = "~buf_$name"

    val initializationCode: String
}

@Serializable
object NoBuffer: Buffer {
    override val name: String
        get() = "<none>"

    override val variableName: String
        get() = "0"

    override val initializationCode: String
        get() = throw UnsupportedOperationException()
}

@Serializable
data class FileBuffer(
    override var name: String,
    @Serializable(with = FileSerializer::class) var referencedFile: File,
    var startFrame: Int = 0, var numFrames: Int = -1,
) : Buffer {
    override val initializationCode: String
        get() = "$variableName = Buffer.read(s, ${referencedFile.superColliderPath}, $startFrame, $numFrames)"
}

@Serializable
data class AllocatedBuffer(
    override var name: String,
    var numFrames: Int, var numChannels: Int = 1
) : Buffer {
    override val initializationCode: String
        get() = "$variableName = Buffer.alloc(s, $numFrames, $numChannels)"
}