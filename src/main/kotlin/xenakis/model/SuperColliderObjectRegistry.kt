package xenakis.model

import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient

abstract class SuperColliderObjectRegistry<O : SuperColliderObject> : ObjectRegistry<O>() {
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