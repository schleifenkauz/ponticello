package xenakis.model.obj

import hextant.context.Context
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient

abstract class AbstractSuperColliderObject : AbstractRenamableObject(), SuperColliderObject {
    protected val client get() = context[SuperColliderClient]

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
    }

    override fun sync() {
        client.run { sync() }
    }

    protected fun redefine() {
        client.run {
            freeObject()
            createObject()
        }
    }

    override fun ScWriter.freeObject() {
        +"if ($superColliderName != nil) { $superColliderName.free; $superColliderName = nil; }"
    }

    override fun onAdded(context: Context) {
        client.run { createObject() }
    }

    override fun onRemoved() {
        initialized = false
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