package xenakis.model.obj

import hextant.context.Context
import hextant.undo.AbstractEdit
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now

abstract class AbstractRenamableObject : RenamableObject, AbstractContextualObject() {
    protected abstract val mutableName: ReactiveVariable<String>

    final override val name: ReactiveValue<String>
        get() = mutableName

    override fun onAdded(context: Context) {}

    override fun onRemoved() {}

    override fun rename(newName: String) {
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