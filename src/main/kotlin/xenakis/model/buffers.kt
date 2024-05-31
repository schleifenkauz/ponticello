package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.FileSerializer
import xenakis.impl.SuperColliderClient
import xenakis.impl.code
import xenakis.impl.superColliderPath
import xenakis.sc.IntegerLiteral
import xenakis.sc.ScExpr
import xenakis.sc.editor.AbstractRenamableObject
import xenakis.ui.XenakisController.Companion.currentProject
import java.io.File

@Serializable
sealed interface BufferObject : RenamableObject {
    val variableName get() = "~buf_${name.now}"

    val initializationCode: String

    override fun createReference(): BufferObjectReference = BufferObjectReference(this)
}

@Serializable
object NoBuffer : BufferObject {
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

    override fun initialize(context: Context) {}
}

@Serializable
sealed class AbstractBuffer : AbstractRenamableObject(), BufferObject {
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
) : AbstractBuffer() {
    val code = code {
        append(variableName)
        append(" = Buffer.read(s, ${referencedFile.superColliderPath}, ")
        startFrame.code(this)
        append(", ")
        numFrames.code(this)
        append(")")
    }

    override val initializationCode: String
        get() = code
}

@Serializable
data class AllocatedBuffer(
    override val mutableName: ReactiveVariable<String>,
    var numFrames: ScExpr = IntegerLiteral(0), var numChannels: ScExpr = IntegerLiteral(1),
) : AbstractBuffer() {
    val code = code {
        append(variableName)
        append(" = Buffer.alloc(s, ")
        numFrames.code(this)
        append(", ")
        numChannels.code(this)
        append(")")
    }

    override val initializationCode: String get() = code
}