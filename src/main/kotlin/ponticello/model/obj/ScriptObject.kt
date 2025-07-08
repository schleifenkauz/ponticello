package ponticello.model.obj

import bundles.set
import fxutils.drag.TypedDataFormat
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.SelectionDistributor
import hextant.context.extend
import hextant.core.editor.defaultState
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import ponticello.model.project.scripts
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.sc.code
import ponticello.sc.editor.CodeBlockEditor
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.util.concurrent.CompletableFuture

@Serializable
class ScriptObject private constructor(
    val root: EditorRoot<@Contextual CodeBlockEditor>,
    val type: ReactiveVariable<Type>,
) : AbstractRenamableObject() {
    override fun initialize(context: Context) {
        super.initialize(context)
        root.initialize(context.extend {
            set(SelectionDistributor, SelectionDistributor.newInstance())
            set(UndoManager, context[UndoManager]/*.createSubManager()*/)
        })
    }

    override val registry: ScriptRegistry
        get() = context.project.scripts

    override val canRename: Boolean
        get() = type.now != Type.BEFORE_BOOT

    override val canDelete: Boolean
        get() = type.now != Type.BEFORE_BOOT

    fun executeContents(client: SuperColliderClient): CompletableFuture<String> {
        val code = root.editor.result.now.code(context)
        return client.eval(code)
    }

    @Serializable
    enum class Type {
        REGULAR,
        BEFORE_BOOT,
        AFTER_BOOT,
        SERVER_TREE;

        override fun toString(): String = when (this) {
            REGULAR -> "Regular"
            BEFORE_BOOT -> "Before Boot"
            AFTER_BOOT -> "After Boot"
            SERVER_TREE -> "Server Tree"
        }
    }

    companion object {
        val DATA_FORMAT = TypedDataFormat<ScriptObjectReference>("ponticello:script")

        fun create(type: Type, name: String): ScriptObject {
            val root = EditorRoot(CodeBlockEditor().defaultState())
            return ScriptObject(root, reactiveVariable(type)).withName(name)
        }
    }
}