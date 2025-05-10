package ponticello.model.obj

import fxutils.undo.AbstractEdit
import fxutils.undo.UndoManager
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now

abstract class AbstractRenamableObject : RenamableObject, AbstractNamedObject() {
    protected abstract val mutableName: ReactiveVariable<String>

    final override val name: ReactiveValue<String>
        get() = mutableName

    override fun canRenameTo(newName: String): Boolean = registry != null && !registry!!.has(newName)

    override fun rename(newName: String) {
        if (newName == name.now) return
        context[UndoManager].record(RenameEdit(this, name.now, newName))
        mutableName.now = newName
    }

    override fun toString(): String = "${javaClass.simpleName} #${name.now}"

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