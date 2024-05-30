package xenakis.sc

import hextant.codegen.Choice
import hextant.codegen.UseEditor
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.FileSerializer
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient
import xenakis.impl.superColliderPath
import xenakis.model.RenamableObject
import xenakis.sc.editor.AbstractRenamableObject
import xenakis.sc.editor.BufferSelector
import xenakis.ui.XenakisController.Companion.currentProject
import java.io.File

@Choice(defaultValue = "Rate.Audio")
enum class Rate {
    Audio, Control;

    override fun toString(): String = when (this) {
        Audio -> "ar"
        Control -> "kr"
    }
}

@Serializable
@UseEditor(BufferSelector::class)
sealed interface Buffer : RenamableObject {
    val variableName get() = "~buf_${name.now}"

    val initializationCode: String
}

@Serializable
object NoBuffer : Buffer {
    override val name: ReactiveValue<String>
        get() = reactiveValue("<none>")

    override fun canRenameTo(newName: String): Boolean = false

    override fun rename(newName: String) {
        throw UnsupportedOperationException("NoBuffer cannot be renamed")
    }

    override val variableName: String
        get() = "0"

    override val initializationCode: String
        get() = throw UnsupportedOperationException("NoBuffer cannot be initialized")
}

@Serializable
sealed class AbstractBuffer : AbstractRenamableObject(), Buffer {
    override fun canRenameTo(newName: String): Boolean = context[currentProject].buffers.hasBuffer(newName)

    override fun rename(newName: String) {
        context[SuperColliderClient].run {
            +"~buf_$newName = $variableName"
            +"$variableName = nil"
        }
        super.rename(newName)
    }
}

@Serializable
data class FileBuffer(
    override val mutableName: ReactiveVariable<String>,
    @Serializable(with = FileSerializer::class) var referencedFile: File,
    var startFrame: ScExpr = IntegerLiteral(0), var numFrames: ScExpr = IntegerLiteral(-1),
) : ScExpr, AbstractBuffer() {
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
data class AllocatedBuffer(
    override val mutableName: ReactiveVariable<String>,
    var numFrames: ScExpr = IntegerLiteral(0), var numChannels: ScExpr = IntegerLiteral(1),
) : ScExpr, AbstractBuffer() {
    override val initializationCode: String = code

    override fun code(writer: ScWriter) {
        writer.append(variableName)
        writer.append(" = Buffer.alloc(s, ")
        numFrames.code(writer)
        numChannels.code(writer)
    }
}