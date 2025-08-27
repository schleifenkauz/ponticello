package ponticello.model.obj

import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run

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

    override fun onLoadedIntoRegistry() {
        super<AbstractRenamableObject>.onLoadedIntoRegistry()
        client.run { createObject() }
    }

    override fun onRemoved() {
        client.run { freeObject() }
    }

    override fun rename(newName: String) {
        val oldVariableName = superColliderName
        super.rename(newName)
        client.run {
            +"$superColliderName = $oldVariableName"
            +"$oldVariableName = nil"
        }
    }
}