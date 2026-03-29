package ponticello.model.code

import bundles.set
import fxutils.drag.TypedDataFormat
import hextant.context.Context
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.model.ctx.PonticelloContext
import ponticello.model.ctx.Scope
import ponticello.model.obj.AbstractSuperColliderObject
import ponticello.model.obj.GlobalPatternReference
import ponticello.model.obj.withName
import ponticello.model.project.busses
import ponticello.sc.EmptyExpr
import ponticello.sc.client.ScWriter
import ponticello.sc.editor.ScExprExpander
import ponticello.ui.launcher.PonticelloLauncher
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
class GlobalPatternObject(
    val patternCode: EditorRoot<@Contextual ScExprExpander>,
) : AbstractSuperColliderObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override fun canRenameTo(newName: String): Boolean = !context[PonticelloLauncher.currentProject].busses.has(newName)

    override fun superColliderName(objectName: String) = "~pattern_${objectName}"

    override val registry: GlobalPatternRegistry
        get() = context[GlobalPatternRegistry]

    override fun initialize(context: Context) {
        super.initialize(context)
        val myContext = context.extend {
            set(PonticelloContext, PonticelloContext.GlobalPattern(this@GlobalPatternObject))
            set(Scope, Scope.createEmpty())
        }
        patternCode.initialize(myContext)
    }

    override fun ScWriter.freeObject() {
        +"$superColliderName = nil"
    }

    override fun ScWriter.sync() {
        createObject()
    }

    override fun copy(): GlobalPatternObject = GlobalPatternObject(patternCode.clone())

    override fun ScWriter.createObject() {
        val code = patternCode.editor.result.now
        append("$superColliderName = ")
        if (code == EmptyExpr) append("nil")
        else code.code(writer, context)
        appendLine(".asStream;")
    }

    companion object {
        fun create(name: String): GlobalPatternObject =
            GlobalPatternObject(EditorRoot(ScExprExpander().defaultState())).withName(name)

        val DATA_FORMAT = TypedDataFormat<GlobalPatternReference>("GlobalPatternObject")
    }
}