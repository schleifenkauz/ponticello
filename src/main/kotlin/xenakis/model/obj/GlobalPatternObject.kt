package xenakis.model.obj

import hextant.context.Context
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import javafx.scene.input.DataFormat
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.project.busses
import xenakis.model.registry.GlobalPatternRegistry
import xenakis.model.registry.NamedObject
import xenakis.sc.EmptyExpr
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.ScExprExpander
import xenakis.ui.launcher.XenakisLauncher

@Serializable
class GlobalPatternObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val patternCode: EditorRoot<@Contextual ScExprExpander>,
) : AbstractSuperColliderObject() {
    override fun canRenameTo(newName: String): Boolean = !context[XenakisLauncher.currentProject].busses.has(newName)

    override val superColliderName: String
        get() = "~pattern_${name.now}"

    override val registry: GlobalPatternRegistry
        get() = context[GlobalPatternRegistry]

    override fun initialize(context: Context) {
        super.initialize(context)
        patternCode.initialize(context)
    }

    override val canCopy: Boolean
        get() = true

    override fun ScWriter.freeObject() {
        +"$superColliderName = nil"
    }

    override fun ScWriter.sync() {
        createObject()
    }

    override fun copy(name: String): NamedObject = GlobalPatternObject(reactiveVariable(name), patternCode.clone())

    override fun ScWriter.createObject() {
        val code = patternCode.editor.result.now
        append("$superColliderName = ")
        if (code == EmptyExpr) append("nil")
        else code.code(writer, context)
        appendLine(".asStream;")
    }

    companion object {
        fun create(name: String): GlobalPatternObject =
            GlobalPatternObject(reactiveVariable(name), EditorRoot(ScExprExpander().defaultState()))

        val DATA_FORMAT = DataFormat("GlobalPatternObject")
    }
}