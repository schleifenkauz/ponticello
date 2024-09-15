package xenakis.model

import hextant.context.Context
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient

abstract class SuperColliderObjectRegistry<O : SuperColliderObject> : ObjectRegistry<O>() {
    override fun initialize(context: Context) {
        super.initialize(context)
        context[SuperColliderClient].run {
            for (obj in objects) {
                obj.run { addToServer() }
            }
        }
    }

    open fun syncAll() {
        context[SuperColliderClient].run {
            for (obj in objects) {
                obj.sync()
            }
        }
    }

    fun ScWriter.allocateAll() {
        for (obj in objects) {
            obj.run { allocateServerObject() }
        }
    }
}