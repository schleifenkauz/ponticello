package xenakis.sc

import hextant.codegen.Choice
import hextant.codegen.UseEditor
import kotlinx.serialization.Serializable
import xenakis.impl.FileSerializer
import xenakis.impl.ScWriter
import xenakis.impl.superColliderPath
import xenakis.sc.editor.BufferSelector
import xenakis.sc.editor.BusSelector
import java.io.File

@Serializable
data class Group(var name: String) {
    val variableName: String get() = if (name == "default") "s.defaultGroup" else "~grp_$name"

    companion object {
        val DEFAULT = Group("default")
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
@UseEditor(BusSelector::class)
data class Bus(
    var name: String,
    var rate: Rate,
    var channels: Int,
) {
    fun copyFrom(obj: Bus) {
        name = obj.name
        rate = obj.rate
        channels = obj.channels
    }

    val variableName get() = if (name != "output") "~bus_$name" else "0"

    val allocationCode get() = "$variableName = Bus.${rate.name.lowercase()}(s, $channels)"

    companion object {
        val output = Bus("output", Rate.Audio, 2)

        val PROPERTY_NAMES = listOf("name", "rate", "channels")
    }
}

@Serializable
@UseEditor(BufferSelector::class)
sealed interface Buffer {
    var name: Identifier

    val variableName get() = "~buf_${name.text}"

    val initializationCode: String
}

@Serializable
object NoBuffer : Buffer {
    override var name: Identifier
        get() = Identifier("<none>")
        set(@Suppress("UNUSED_PARAMETER") value) {
            throw UnsupportedOperationException("NoBuffer cannot be renamed")
        }

    override val variableName: String
        get() = "0"

    override val initializationCode: String
        get() = throw UnsupportedOperationException("NoBuffer cannot be initialized")
}

@Serializable
/*@Compound(nodeType = ScExpr::class, serializable = true)*/
data class FileBuffer(
    override var name: Identifier,
    @Serializable(with = FileSerializer::class) var referencedFile: File,
    var startFrame: ScExpr = IntegerLiteral(0), var numFrames: ScExpr = IntegerLiteral(-1),
) : Buffer, ScExpr {
    override fun code(writer: ScWriter) = with(writer) {
        append(variableName)
        append(" = Buffer.read(s, ${referencedFile.superColliderPath}, ")
        startFrame.code(writer)
        append(", ")
        numFrames.code(writer)
        append(")")
    }

    override val initializationCode: String
        get() = code
}

@Serializable
/*@Compound(nodeType = ScExpr::class, serializable = true)*/
data class AllocatedBuffer(
    override var name: Identifier,
    var numFrames: ScExpr = IntegerLiteral(0), var numChannels: ScExpr = IntegerLiteral(1),
) : Buffer, ScExpr {
    override val initializationCode: String = code

    override fun code(writer: ScWriter) {
        writer.append(variableName)
        writer.append(" = Buffer.alloc(s, ")
        numFrames.code(writer)
        numChannels.code(writer)
    }
}