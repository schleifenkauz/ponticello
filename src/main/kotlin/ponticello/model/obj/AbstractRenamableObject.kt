package ponticello.model.obj

import fxutils.undo.AbstractEdit
import fxutils.undo.UndoManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
abstract class AbstractRenamableObject : RenamableObject, AbstractNamedObject() {
    @SerialName("name")
    private lateinit var _name: ReactiveVariable<String>

    override val name: ReactiveValue<String>
        get() = _name

    override fun setInitialName(name: String) {
        check(!initialized) { "Cannot set initial name when $this is already initialized" }
        _name = reactiveVariable(name)
    }

    override fun canRenameTo(newName: String): Boolean = registry != null && !registry!!.has(newName)

    override fun rename(newName: String) {
        if (newName == name.now) return
        context[UndoManager].record(RenameEdit(this, name.now, newName))
        _name.now = newName
    }

    override fun copy(): RenamableObject = throw UnsupportedOperationException("Cannot copy $this")

    private class RenameEdit(
        val obj: AbstractRenamableObject,
        val oldName: String, val newName: String
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Rename object"

        override fun doUndo() {
            obj.rename(oldName)
        }

        override fun doRedo() {
            obj.rename(newName)
        }
    }
}