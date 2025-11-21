package ponticello.model.obj

import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import reaktive.value.now

abstract class AbstractSuperColliderObject : AbstractRenamableObject(), SuperColliderObject {
    protected val client get() = context[SuperColliderClient]

    override fun sync() {
        client.run {
            sync()
        }
    }

    override fun ScWriter.freeObject() {
        +"if ($superColliderName != nil) { $superColliderName.free; $superColliderName = nil; }"
    }

    override fun onRemoved() {
        client.run { freeObject() }
    }

    override fun rename(newName: String) {
        val oldName = name.now
        super.rename(newName)
        onRename(oldName, newName)
    }

    protected open fun onRename(oldName: String, newName: String) {
        val oldVariableName = superColliderName(oldName)
        client.run {
            +"$superColliderName = $oldVariableName"
            +"$oldVariableName = nil"
        }
    }
}