package xenakis.model

import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient

abstract class SuperColliderObjectRegistry<O : SuperColliderObject> : ObjectRegistry<O>() {
    open fun syncAll() {
        context[SuperColliderClient].run {
            for (obj in objects) {
                obj.run {
                    freeServerObject()
                    allocateServerObject()
                }
            }
        }
    }

    fun initialize(writer: ScWriter) {
        for (obj in objects) {
            obj.run { writer.allocateServerObject() }
        }
    }

    override fun onRemoved(obj: O, idx: Int) {
        obj.remove()
    }
}